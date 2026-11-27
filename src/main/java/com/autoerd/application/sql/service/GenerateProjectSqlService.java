package com.autoerd.application.sql.service;

import com.autoerd.application.sql.usecase.GenerateProjectSqlCommand;
import com.autoerd.application.sql.usecase.GenerateProjectSqlUseCase;
import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlGenerationPort;
import com.autoerd.domain.sql.port.SqlMetadataProvider;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
import com.autoerd.domain.sql.port.SqlTableResolverPort;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import com.autoerd.service.TableCandidatePrefilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateProjectSqlService implements GenerateProjectSqlUseCase {

    private final SqlMetadataProvider metadataProvider;
    private final SqlTableResolverPort tableResolver;
    private final SqlGenerationPort sqlGenerationPort;
    private final TableCandidatePrefilter tableCandidatePrefilter;

    @Override
    public Flux<String> generateSql(GenerateProjectSqlCommand command) {
        SqlMetadataSnapshot snapshot = metadataProvider.getCurrentSnapshot(command.projectId());

        if (snapshot.tables().isEmpty()) {
            return Flux.just("-- 스키마 정보가 없습니다. 먼저 엑셀 파일을 업로드해 주세요.");
        }

        // Step1 전 후보 사전 축소(pre-filter): 대형 스키마에서 프롬프트 크기를 낮춰 Step1 지연을 줄인다.
        // 소형 스키마/매칭 0건이면 전체를 그대로 넘기는 폴백이 내장되어 있어 회귀가 없다.
        List<TableSchema> candidateTables =
                tableCandidatePrefilter.prefilter(command.query(), snapshot.tables(), snapshot.relations());

        List<String> candidateNames = candidateTables.stream()
                .map(TableSchema::getTableName)
                .toList();

        Map<String, String> candidateDescriptions = new HashMap<>();
        for (TableSchema table : candidateTables) {
            if (table.getTableDescription() != null) {
                candidateDescriptions.putIfAbsent(table.getTableName(), table.getTableDescription());
            }
        }

        SqlTableResolutionRequest resolutionReq = new SqlTableResolutionRequest(
                command.projectId(), command.query(), candidateNames, candidateDescriptions);
        List<String> relevantNames;
        long step1Start = System.currentTimeMillis();
        try {
            relevantNames = tableResolver.resolveRelevantTableNames(resolutionReq);
        } catch (SqlTableResolutionException e) {
            // LLM 호출 실패는 "관련 테이블 없음"(insufficient_schema)과 다른 응답으로 구분한다.
            log.warn("Step1 테이블 판별 실패로 SQL 생성 중단: projectId={}, elapsedMs={}, cause={}",
                    command.projectId(), System.currentTimeMillis() - step1Start,
                    e.getCause() != null ? e.getCause().getClass().getName() : e.getClass().getName());
            return Flux.just("-- llm_error: 관련 테이블 판별 중 LLM 호출에 실패했습니다.\n"
                    + "-- LLM 서버 상태를 확인한 뒤 다시 시도해 주세요.");
        }

        if (relevantNames.isEmpty()) {
            return Flux.just("-- insufficient_schema: 요청에 해당하는 테이블을 스키마에서 찾을 수 없습니다.\n"
                    + "-- 업로드된 스키마에 관련 테이블이 있는지 확인해 주세요.");
        }

        log.info("관련 테이블 [{}] 해결됨: projectId={}, step1ElapsedMs={}",
                String.join(", ", relevantNames), command.projectId(), System.currentTimeMillis() - step1Start);

        Set<String> relevantSet = new HashSet<>(relevantNames);
        List<TableSchema> relevantTables = snapshot.tables().stream()
                .filter(t -> relevantSet.contains(t.getTableName()))
                .collect(Collectors.toList());
        List<TableRelation> relevantRelations = snapshot.relations().stream()
                .filter(r -> relevantSet.contains(r.getFromTable()) && relevantSet.contains(r.getToTable()))
                .collect(Collectors.toList());

        SqlMetadataSnapshot filteredSnapshot = new SqlMetadataSnapshot(relevantTables, relevantRelations, snapshot.dialect());
        SqlGenerationRequest genReq = new SqlGenerationRequest(command.projectId(), command.query(), filteredSnapshot);

        return sqlGenerationPort.generate(genReq);
    }
}
