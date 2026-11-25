package com.autoerd.domain.sql.model;

import java.util.List;
import java.util.Map;

/**
 * Step1 테이블 선별 요청.
 *
 * @param candidateTableNames        후보 테이블명 (LLM 응답 검증 기준)
 * @param candidateTableDescriptions 테이블명 -> 테이블 설명. 설명이 없는 테이블은 키가 없어도 된다
 */
public record SqlTableResolutionRequest(
        Long projectId,
        String query,
        List<String> candidateTableNames,
        Map<String, String> candidateTableDescriptions
) {

    public SqlTableResolutionRequest {
        candidateTableDescriptions = candidateTableDescriptions == null
                ? Map.of()
                : candidateTableDescriptions;
    }

    public SqlTableResolutionRequest(Long projectId, String query, List<String> candidateTableNames) {
        this(projectId, query, candidateTableNames, Map.of());
    }
}
