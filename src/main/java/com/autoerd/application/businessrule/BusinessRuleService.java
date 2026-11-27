package com.autoerd.application.businessrule;

import com.autoerd.application.project.ProjectService;
import com.autoerd.domain.businessrule.model.BusinessRuleType;
import com.autoerd.domain.sql.port.SqlMetadataProvider;
import com.autoerd.exception.AppException;
import com.autoerd.infrastructure.persistence.jpa.entity.ProjectBusinessRuleEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.ProjectBusinessRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 프로젝트 업무 규칙 CRUD 와 미리보기 (이슈 #24 W1).
 *
 * <p>규칙 원문은 로그에 남기지 않는다. 규칙 ID, 유형, 건수만 남긴다.
 */
@Slf4j
@Service
public class BusinessRuleService {

    static final int MAX_VALUE_ITEMS_JSON = 4000;
    private static final TypeReference<List<BusinessRuleValueItem>> VALUE_ITEMS_TYPE = new TypeReference<>() {
    };

    private final ProjectBusinessRuleRepository ruleRepository;
    private final ProjectService projectService;
    private final SqlMetadataProvider metadataProvider;
    private final ObjectMapper objectMapper;
    private final int maxRulesPerProject;

    public BusinessRuleService(ProjectBusinessRuleRepository ruleRepository,
                               ProjectService projectService,
                               SqlMetadataProvider metadataProvider,
                               ObjectMapper objectMapper,
                               @Value("${app.business-rules.max-per-project:500}") int maxRulesPerProject) {
        this.ruleRepository = ruleRepository;
        this.projectService = projectService;
        this.metadataProvider = metadataProvider;
        this.objectMapper = objectMapper;
        this.maxRulesPerProject = maxRulesPerProject;
    }

    /**
     * @param type  유형 필터 (선택, 대소문자 무시)
     * @param table 테이블 필터 (선택). tableName 또는 relatedTableName 이 같은 규칙 (대소문자 무시)
     */
    @Transactional(readOnly = true)
    public List<BusinessRuleResponse> list(Long projectId, String type, String table) {
        projectService.getProject(projectId);
        String rawType = BusinessRuleValidator.trimToNull(type);
        BusinessRuleType typeFilter = rawType == null ? null : BusinessRuleValidator.parseType(rawType, "type");
        String rawTable = BusinessRuleValidator.trimToNull(table);
        String tableKey = rawTable == null ? null : MetadataNameIndex.key(rawTable);

        MetadataNameIndex index = currentIndex(projectId);
        return ruleRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(projectId).stream()
                .filter(r -> typeFilter == null || r.getRuleType() == typeFilter)
                .filter(r -> tableKey == null || matchesTable(r, tableKey))
                .map(r -> toResponse(r, index))
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessRuleResponse get(Long projectId, Long ruleId) {
        projectService.getProject(projectId);
        return toResponse(findRule(projectId, ruleId), currentIndex(projectId));
    }

    @Transactional
    public BusinessRuleResponse create(Long projectId, BusinessRuleRequest request) {
        projectService.getProject(projectId);
        BusinessRuleDraft draft = BusinessRuleValidator.validate(request);
        if (ruleRepository.countByProjectIdAndIsDeletedFalse(projectId) >= maxRulesPerProject) {
            throw AppException.badRequest("업무 규칙은 프로젝트당 " + maxRulesPerProject + "개까지 등록할 수 있습니다");
        }
        MetadataNameIndex index = currentIndex(projectId);
        ResolvedNames names = resolveNames(draft, index, null);

        ProjectBusinessRuleEntity entity = ProjectBusinessRuleEntity.builder()
                .projectId(projectId)
                .build();
        apply(entity, draft, names);
        ProjectBusinessRuleEntity saved = ruleRepository.save(entity);
        log.info("업무 규칙 등록: projectId={}, ruleId={}, ruleType={}", projectId, saved.getId(), saved.getRuleType());
        return toResponse(saved, index);
    }

    @Transactional
    public BusinessRuleResponse update(Long projectId, Long ruleId, BusinessRuleRequest request) {
        projectService.getProject(projectId);
        ProjectBusinessRuleEntity entity = findRule(projectId, ruleId);
        BusinessRuleDraft draft = BusinessRuleValidator.validate(request);
        MetadataNameIndex index = currentIndex(projectId);
        ResolvedNames names = resolveNames(draft, index, entity);

        apply(entity, draft, names);
        ProjectBusinessRuleEntity saved = ruleRepository.saveAndFlush(entity);
        log.info("업무 규칙 수정: projectId={}, ruleId={}, ruleType={}, enabled={}",
                projectId, saved.getId(), saved.getRuleType(), saved.getIsEnabled());
        return toResponse(saved, index);
    }

    @Transactional
    public void delete(Long projectId, Long ruleId) {
        projectService.getProject(projectId);
        ProjectBusinessRuleEntity entity = findRule(projectId, ruleId);
        entity.setIsDeleted(true);
        entity.setDeletedAt(LocalDateTime.now());
        ruleRepository.save(entity);
        log.info("업무 규칙 삭제: projectId={}, ruleId={}", projectId, ruleId);
    }

    /** LLM 호출 없이 질의와 테이블 목록에 대해 강제 포함/자동 포함 테이블과 주입될 규칙을 계산한다. */
    @Transactional(readOnly = true)
    public BusinessRulePreviewResponse preview(Long projectId, BusinessRulePreviewRequest request) {
        projectService.getProject(projectId);
        String query = request == null ? null : BusinessRuleValidator.trimToNull(request.query());
        if (query == null) {
            throw AppException.badRequest("query는 필수입니다");
        }
        MetadataNameIndex index = currentIndex(projectId);
        List<String> tables = request.tables() == null ? List.of() : request.tables().stream()
                .filter(Objects::nonNull)
                .map(index::table)
                .flatMap(Optional::stream)
                .toList();
        List<BusinessRuleResponse> rules = ruleRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(projectId)
                .stream()
                .map(r -> toResponse(r, index))
                .toList();
        BusinessRulePreviewResponse result = BusinessRuleSelector.select(rules, query, tables);
        log.info("업무 규칙 미리보기: projectId={}, matchedTerms={}, injectedRules={}, skippedRules={}",
                projectId, result.matchedTerms().size(), result.rules().size(), result.skippedRuleCount());
        return result;
    }

    private ProjectBusinessRuleEntity findRule(Long projectId, Long ruleId) {
        return ruleRepository.findByIdAndProjectIdAndIsDeletedFalse(ruleId, projectId)
                .orElseThrow(() -> new AppException("업무 규칙을 찾을 수 없습니다: " + ruleId, HttpStatus.NOT_FOUND));
    }

    private MetadataNameIndex currentIndex(Long projectId) {
        return MetadataNameIndex.of(metadataProvider.getCurrentSnapshot(projectId));
    }

    private static boolean matchesTable(ProjectBusinessRuleEntity rule, String tableKey) {
        return tableKey.equals(MetadataNameIndex.key(rule.getTableName()))
                || (rule.getRelatedTableName() != null
                && tableKey.equals(MetadataNameIndex.key(rule.getRelatedTableName())));
    }

    private record ResolvedNames(String tableName, String columnName, String relatedTableName) {
    }

    /**
     * 테이블/컬럼/함께 쓰는 테이블이 현재 활성 메타데이터에 있는지 확인하고 메타데이터 원래 표기로 바꾼다 (D12).
     * 수정 시에는 기존 값과 같은 이름(대소문자 무시)은 검사하지 않는다. 재업로드로 대상이 사라진 규칙도
     * 비활성화나 설명 수정은 할 수 있게 하기 위해서다.
     */
    private static ResolvedNames resolveNames(BusinessRuleDraft draft, MetadataNameIndex index,
                                              ProjectBusinessRuleEntity existing) {
        String tableName = draft.tableName();
        boolean tableUnchanged = existing != null && sameName(tableName, existing.getTableName());
        if (tableUnchanged) {
            tableName = existing.getTableName();
        } else {
            tableName = index.table(tableName)
                    .orElseThrow(() -> AppException.badRequest("테이블을 찾을 수 없습니다: " + draft.tableName()));
        }

        String columnName = draft.columnName();
        if (columnName != null) {
            if (tableUnchanged && sameName(columnName, existing.getColumnName())) {
                columnName = existing.getColumnName();
            } else {
                String table = tableName;
                columnName = index.column(table, columnName)
                        .orElseThrow(() -> AppException.badRequest(
                                "컬럼을 찾을 수 없습니다: " + table + "." + draft.columnName()));
            }
        }

        String relatedTableName = draft.relatedTableName();
        if (relatedTableName != null) {
            if (existing != null && sameName(relatedTableName, existing.getRelatedTableName())) {
                relatedTableName = existing.getRelatedTableName();
            } else {
                relatedTableName = index.table(relatedTableName)
                        .orElseThrow(() -> AppException.badRequest(
                                "함께 쓰는 테이블을 찾을 수 없습니다: " + draft.relatedTableName()));
            }
        }
        return new ResolvedNames(tableName, columnName, relatedTableName);
    }

    private static boolean sameName(String a, String b) {
        return a != null && b != null && MetadataNameIndex.key(a).equals(MetadataNameIndex.key(b));
    }

    private void apply(ProjectBusinessRuleEntity entity, BusinessRuleDraft draft, ResolvedNames names) {
        entity.setRuleType(draft.ruleType());
        entity.setTerm(draft.term());
        entity.setTermAliases(draft.termAliases());
        entity.setTableName(names.tableName());
        entity.setColumnName(names.columnName());
        entity.setRelatedTableName(names.relatedTableName());
        entity.setConditionText(draft.conditionText());
        entity.setValueItemsJson(toJson(draft.valueItems()));
        entity.setAutoInclude(draft.autoInclude());
        entity.setDescription(draft.description());
        entity.setIsEnabled(draft.enabled());
    }

    private String toJson(List<BusinessRuleValueItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(items);
            if (json.length() > MAX_VALUE_ITEMS_JSON) {
                throw AppException.badRequest("valueItems 전체 길이가 너무 깁니다 (JSON " + MAX_VALUE_ITEMS_JSON + "자 이하)");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw AppException.badRequest("valueItems 형식이 올바르지 않습니다");
        }
    }

    private List<BusinessRuleValueItem> fromJson(Long ruleId, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(objectMapper.readValue(json, VALUE_ITEMS_TYPE));
        } catch (JsonProcessingException e) {
            log.warn("업무 규칙 값 목록 파싱 실패: ruleId={}", ruleId);
            return List.of();
        }
    }

    private BusinessRuleResponse toResponse(ProjectBusinessRuleEntity e, MetadataNameIndex index) {
        return new BusinessRuleResponse(
                e.getId(),
                e.getRuleType(),
                e.getTerm(),
                e.getTermAliases(),
                e.getTableName(),
                e.getColumnName(),
                e.getRelatedTableName(),
                e.getConditionText(),
                fromJson(e.getId(), e.getValueItemsJson()),
                Boolean.TRUE.equals(e.getAutoInclude()),
                e.getDescription(),
                Boolean.TRUE.equals(e.getIsEnabled()),
                index.statusOf(e.getTableName(), e.getColumnName(), e.getRelatedTableName()),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
