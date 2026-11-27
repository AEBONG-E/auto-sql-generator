package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleTargetStatus;
import com.autoerd.domain.businessrule.model.BusinessRuleType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 업무 규칙 응답. targetStatus 는 저장값이 아니라 조회 시점의 활성 메타데이터로 계산한다.
 * valueItems 는 값이 없으면 빈 배열이다.
 */
public record BusinessRuleResponse(
        Long id,
        BusinessRuleType ruleType,
        String term,
        String termAliases,
        String tableName,
        String columnName,
        String relatedTableName,
        String conditionText,
        List<BusinessRuleValueItem> valueItems,
        boolean autoInclude,
        String description,
        boolean enabled,
        BusinessRuleTargetStatus targetStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
