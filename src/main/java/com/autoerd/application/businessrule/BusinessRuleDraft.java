package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleType;

import java.util.List;

/**
 * 형식 검증과 정규화(trim, 빈 문자열 -> null, 유형에 쓰이지 않는 필드 제거)를 마친 요청.
 * 테이블/컬럼 존재 검증은 아직 하지 않은 상태다.
 */
record BusinessRuleDraft(
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
        boolean enabled
) {
}
