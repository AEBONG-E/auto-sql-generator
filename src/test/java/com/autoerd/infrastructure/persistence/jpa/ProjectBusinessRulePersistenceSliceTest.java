package com.autoerd.infrastructure.persistence.jpa;

import com.autoerd.domain.businessrule.model.BusinessRuleType;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataProjectEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.ProjectBusinessRuleEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataProjectRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.ProjectBusinessRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 이슈 #24: project_business_rule 엔티티와 schema-h2.sql DDL 의 일치 확인 (ddl-auto: none).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProjectBusinessRulePersistenceSliceTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private MetadataProjectRepository projectRepository;
    @Autowired
    private ProjectBusinessRuleRepository ruleRepository;

    private Long persistProject(String key) {
        return projectRepository.saveAndFlush(MetadataProjectEntity.builder()
                .projectKey(key).projectName(key).projectStatus("ACTIVE").build()).getId();
    }

    @Test
    @DisplayName("모든 컬럼을 저장하고 다시 읽으며, 기본값(autoInclude=false, enabled=true, 감사 컬럼)을 채운다")
    void persistsAllColumns() {
        Long projectId = persistProject("br-slice");
        String json = "[{\"code\":\"C1\",\"label\":\"" + "가".repeat(3900) + "\"}]";

        Long id = ruleRepository.saveAndFlush(ProjectBusinessRuleEntity.builder()
                .projectId(projectId)
                .ruleType(BusinessRuleType.TERM)
                .term("취소 주문")
                .termAliases("취소된 주문, 주문 취소")
                .tableName("shop_order")
                .columnName("order_status")
                .conditionText("order_status IN ('C1', 'C2')")
                .valueItemsJson(json)
                .description("환불 완료 건도 취소로 본다")
                .build()).getId();
        em.clear();

        ProjectBusinessRuleEntity found = ruleRepository.findByIdAndProjectIdAndIsDeletedFalse(id, projectId).orElseThrow();
        assertThat(found.getRuleType()).isEqualTo(BusinessRuleType.TERM);
        assertThat(found.getTermAliases()).isEqualTo("취소된 주문, 주문 취소");
        assertThat(found.getValueItemsJson()).isEqualTo(json);
        assertThat(found.getAutoInclude()).isFalse();
        assertThat(found.getIsEnabled()).isTrue();
        assertThat(found.getIsDeleted()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getRelatedTableName()).isNull();
    }

    @Test
    @DisplayName("soft delete 한 규칙은 조회/건수에서 빠지고, 다른 프로젝트 id 로는 조회되지 않는다")
    void softDeleteAndProjectScope() {
        Long projectId = persistProject("br-slice-2");
        Long otherId = persistProject("br-slice-3");
        ProjectBusinessRuleEntity kept = ruleRepository.saveAndFlush(rule(projectId, "shop_order"));
        ProjectBusinessRuleEntity removed = ruleRepository.saveAndFlush(rule(projectId, "shop_payment"));
        removed.setIsDeleted(true);
        removed.setDeletedAt(LocalDateTime.now());
        ruleRepository.saveAndFlush(removed);

        assertThat(ruleRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(projectId))
                .extracting(ProjectBusinessRuleEntity::getId).containsExactly(kept.getId());
        assertThat(ruleRepository.countByProjectIdAndIsDeletedFalse(projectId)).isEqualTo(1);
        assertThat(ruleRepository.findByIdAndProjectIdAndIsDeletedFalse(removed.getId(), projectId)).isEmpty();
        assertThat(ruleRepository.findByIdAndProjectIdAndIsDeletedFalse(kept.getId(), otherId)).isEmpty();
    }

    @Test
    @DisplayName("없는 프로젝트를 가리키면 FK 제약으로 저장되지 않는다")
    void foreignKeyToProject() {
        assertThatThrownBy(() -> ruleRepository.saveAndFlush(rule(987654L, "shop_order")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ProjectBusinessRuleEntity rule(Long projectId, String table) {
        return ProjectBusinessRuleEntity.builder()
                .projectId(projectId)
                .ruleType(BusinessRuleType.TABLE_DEFAULT_FILTER)
                .tableName(table)
                .conditionText("deleted_yn = 'N'")
                .build();
    }
}
