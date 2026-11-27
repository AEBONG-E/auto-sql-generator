package com.autoerd.application.businessrule;

import java.util.List;

/**
 * 업무 규칙 등록/수정 요청 (POST, PUT 공통). PUT 은 전체 수정이다.
 * 유형에 쓰이지 않는 필드는 저장하지 않는다 (BusinessRuleValidator 참고).
 *
 * @param ruleType    TERM | TABLE_DEFAULT_FILTER | PERIOD_COLUMN | JOIN_WITH | COLUMN_VALUES (대소문자 무시)
 * @param autoInclude JOIN_WITH 에서만 쓴다. null 이면 false
 * @param enabled     null 이면 true
 */
public record BusinessRuleRequest(
        String ruleType,
        String term,
        String termAliases,
        String tableName,
        String columnName,
        String relatedTableName,
        String conditionText,
        List<BusinessRuleValueItem> valueItems,
        Boolean autoInclude,
        String description,
        Boolean enabled
) {
}
