package com.autoerd.domain.businessrule.model;

/**
 * 규칙이 가리키는 테이블/컬럼이 현재 활성 메타데이터에 있는지 (이슈 #24).
 * 저장하지 않고 조회할 때마다 계산한다. OK 가 아닌 규칙은 프롬프트 주입에서 제외한다.
 */
public enum BusinessRuleTargetStatus {
    OK,
    TABLE_MISSING,
    COLUMN_MISSING,
    RELATED_TABLE_MISSING
}
