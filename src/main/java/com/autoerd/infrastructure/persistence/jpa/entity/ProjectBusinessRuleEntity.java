package com.autoerd.infrastructure.persistence.jpa.entity;

import com.autoerd.domain.businessrule.model.BusinessRuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프로젝트 업무 규칙 (이슈 #24, Flyway V3). 테이블/컬럼은 이름으로 가리킨다.
 */
@Getter
@Setter
@Builder
@Entity
@Table(
        name = "project_business_rule",
        indexes = {
                @Index(name = "idx_business_rule_project", columnList = "project_id, is_deleted, is_enabled")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProjectBusinessRuleEntity extends SoftDeletableAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 30)
    private BusinessRuleType ruleType;

    @Column(name = "term", length = 100)
    private String term;

    @Column(name = "term_aliases", length = 300)
    private String termAliases;

    @Column(name = "table_name", nullable = false, length = 255)
    private String tableName;

    @Column(name = "column_name", length = 255)
    private String columnName;

    @Column(name = "related_table_name", length = 255)
    private String relatedTableName;

    @Column(name = "condition_text", length = 1000)
    private String conditionText;

    @Column(name = "value_items_json", length = 4000)
    private String valueItemsJson;

    @Column(name = "auto_include", nullable = false)
    @Builder.Default
    private Boolean autoInclude = Boolean.FALSE;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = Boolean.TRUE;
}
