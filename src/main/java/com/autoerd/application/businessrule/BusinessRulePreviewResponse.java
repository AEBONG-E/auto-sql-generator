package com.autoerd.application.businessrule;

import java.util.List;

/**
 * 미리보기 응답.
 *
 * @param matchedTerms       질의에 용어/별칭이 들어 있어 매칭된 TERM 규칙
 * @param forcedTables       매칭된 TERM 규칙 때문에 Step1 후보에 강제 포함될 테이블
 * @param autoIncludedTables JOIN_WITH(autoInclude) 로 자동 추가될 테이블 (1-hop, 최대 3개)
 * @param selectedTables     tables + forcedTables + autoIncludedTables
 * @param rules              selectedTables 기준으로 Step2 에 주입될 규칙 (사용 중이고 targetStatus=OK 인 것만)
 * @param skippedRuleCount   사용 중이지만 targetStatus 가 OK 가 아니어서 주입에서 빠지는 규칙 수
 */
public record BusinessRulePreviewResponse(
        List<TermMatch> matchedTerms,
        List<String> forcedTables,
        List<String> autoIncludedTables,
        List<String> selectedTables,
        List<BusinessRuleResponse> rules,
        int skippedRuleCount
) {

    /**
     * @param matchedText 질의와 맞은 용어 또는 별칭
     */
    public record TermMatch(Long ruleId, String term, String matchedText, String tableName) {
    }
}
