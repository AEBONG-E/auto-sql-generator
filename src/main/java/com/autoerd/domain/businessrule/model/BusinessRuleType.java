package com.autoerd.domain.businessrule.model;

/**
 * 프로젝트 업무 규칙 유형 (이슈 #24). 예시 쿼리(EXAMPLE_QUERY)는 #5 로 분리해 여기에 두지 않는다.
 */
public enum BusinessRuleType {
    /** 용어 정의 -> 조건식 또는 값 목록 */
    TERM,
    /** 테이블 조회 시 기본 조건 */
    TABLE_DEFAULT_FILTER,
    /** 기간 조회 기준 컬럼 */
    PERIOD_COLUMN,
    /** 함께 쓰는 테이블 */
    JOIN_WITH,
    /** 컬럼 값 목록 보강 (코드 -> 의미) */
    COLUMN_VALUES
}
