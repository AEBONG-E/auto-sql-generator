package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleTargetStatus;
import com.autoerd.domain.businessrule.model.BusinessRuleType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 업무 규칙 선택 로직 (이슈 #24 설계 6.1, LLM 무관 순수 함수).
 *
 * <p>W1 에서는 preview API 에 필요한 최소 구현만 둔다: 용어 매칭 -> 강제 포함 테이블,
 * JOIN_WITH(autoInclude) 1-hop 자동 포함(상한 3), 선택 테이블 기준 주입 대상 선택.
 * 규칙 블록 문자 상한과 우선순위 절단, 생성 흐름 연결은 W2 에서 완성한다.
 */
final class BusinessRuleSelector {

    static final int MAX_AUTO_INCLUDED_TABLES = 3;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private BusinessRuleSelector() {
    }

    /**
     * @param rules       프로젝트의 미삭제 규칙 전체 (targetStatus 계산 완료)
     * @param query       사용자 질의
     * @param givenTables Step1 결과를 대신하는 테이블 (메타데이터 원래 표기로 정규화된 것)
     */
    static BusinessRulePreviewResponse select(List<BusinessRuleResponse> rules, String query, List<String> givenTables) {
        List<BusinessRuleResponse> active = rules.stream()
                .filter(BusinessRuleResponse::enabled)
                .filter(r -> r.targetStatus() == BusinessRuleTargetStatus.OK)
                .toList();
        int skipped = (int) rules.stream()
                .filter(BusinessRuleResponse::enabled)
                .filter(r -> r.targetStatus() != BusinessRuleTargetStatus.OK)
                .count();

        // 1. 용어 매칭 -> 강제 포함
        String normalizedQuery = normalize(query);
        List<BusinessRulePreviewResponse.TermMatch> matches = new ArrayList<>();
        Map<String, String> forced = new LinkedHashMap<>();
        for (BusinessRuleResponse rule : active) {
            if (rule.ruleType() != BusinessRuleType.TERM) {
                continue;
            }
            String matched = matchedText(rule, normalizedQuery);
            if (matched != null) {
                matches.add(new BusinessRulePreviewResponse.TermMatch(
                        rule.id(), rule.term(), matched, rule.tableName()));
                forced.putIfAbsent(MetadataNameIndex.key(rule.tableName()), rule.tableName());
            }
        }

        Map<String, String> base = new LinkedHashMap<>();
        givenTables.forEach(t -> base.putIfAbsent(MetadataNameIndex.key(t), t));
        forced.forEach(base::putIfAbsent);

        // 2. JOIN_WITH autoInclude 1-hop (base 기준, 상한 3)
        Map<String, String> autoIncluded = new LinkedHashMap<>();
        for (BusinessRuleResponse rule : active) {
            if (autoIncluded.size() >= MAX_AUTO_INCLUDED_TABLES) {
                break;
            }
            if (rule.ruleType() != BusinessRuleType.JOIN_WITH || !rule.autoInclude()) {
                continue;
            }
            String related = MetadataNameIndex.key(rule.relatedTableName());
            if (base.containsKey(MetadataNameIndex.key(rule.tableName())) && !base.containsKey(related)) {
                autoIncluded.putIfAbsent(related, rule.relatedTableName());
            }
        }

        Map<String, String> selected = new LinkedHashMap<>(base);
        autoIncluded.forEach(selected::putIfAbsent);

        // 3. 주입 대상: 선택 테이블에 걸린 규칙. JOIN_WITH 는 양쪽이 모두 선택된 경우만.
        List<BusinessRuleResponse> injected = active.stream()
                .filter(r -> selected.containsKey(MetadataNameIndex.key(r.tableName())))
                .filter(r -> r.ruleType() != BusinessRuleType.JOIN_WITH
                        || selected.containsKey(MetadataNameIndex.key(r.relatedTableName())))
                .toList();

        return new BusinessRulePreviewResponse(
                List.copyOf(matches),
                List.copyOf(forced.values()),
                List.copyOf(autoIncluded.values()),
                List.copyOf(selected.values()),
                injected,
                skipped);
    }

    /** 공백 제거 + 대소문자 무시 부분 문자열. 용어를 먼저, 그다음 별칭을 본다. */
    private static String matchedText(BusinessRuleResponse rule, String normalizedQuery) {
        List<String> candidates = new ArrayList<>();
        candidates.add(rule.term());
        if (rule.termAliases() != null) {
            for (String alias : rule.termAliases().split(",")) {
                candidates.add(alias.trim());
            }
        }
        for (String candidate : candidates) {
            String normalized = normalize(candidate);
            if (!normalized.isEmpty() && normalizedQuery.contains(normalized)) {
                return candidate;
            }
        }
        return null;
    }

    static String normalize(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }
}
