package com.autoerd.service;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Step1(테이블 매핑) LLM 호출 전에 후보 테이블을 사전 축소하는 결정적(비-LLM) 필터.
 *
 * <p>배경: 대형 스키마(90테이블·1,421컬럼 실측)에서 전체 테이블을 Step1 프롬프트에 넣으면
 * gemma4의 prompt_eval 토큰이 급증해 Step1 지연이 33~75초까지 커진다. 사용자 질의와 관련된
 * 테이블만 후보로 좁혀 프롬프트 크기를 낮춘다.
 *
 * <p><b>정확도 최우선(recall-first) 정책</b>: 속도보다 관련 테이블 누락 방지가 우선이다.
 * 다음 다중 안전장치로 pre-filter ON/OFF의 최종 SQL이 의미상 동일하도록 recall을 최대화한다.
 * <ul>
 *   <li><b>동의어 사전</b> — 한글 질의("지점")를 영문 식별자 토큰(branch)으로 확장(한글↔영문 간극 해소).</li>
 *   <li><b>부분·양방향 매칭</b> — 질의 토큰과 테이블/컬럼 토큰의 부분일치(어간 유사)까지 허용해 매칭 recall↑.</li>
 *   <li><b>비율 기반 상한</b> — 실효 상한 = max(maxCandidates, ceil(전체×ratio)). 기본 max(50, 70%).</li>
 *   <li><b>관계 1-hop 이웃 무조건 포함</b> — 코어/브리지 테이블은 스코어 0이어도 FK로 연결되면 포함,
 *       실효 상한을 넘어 전체 크기까지 허용.</li>
 *   <li><b>0건 폴백</b> — 매칭이 전무하면 전체를 그대로 넘긴다.</li>
 *   <li><b>소형 스키마 no-op</b> — {@code min-tables} 이하는 미적용(기존 동작 보존).</li>
 * </ul>
 * 최종 선별은 여전히 Step1 LLM + {@code validNames} 서버측 필터가 담당한다.
 * {@code app.llm.prefilter.enabled=false}로 완전히 끌 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableCandidatePrefilter {

    /** 질의/식별자 토큰 분리: 유니코드 문자/숫자가 아닌 경계로 분리(한글·영문 혼용 대응). */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final PrefilterProperties props;

    public List<TableSchema> prefilter(String query, List<TableSchema> allTables, List<TableRelation> relations) {
        if (!props.isEnabled() || allTables == null || allTables.size() <= props.getMinTables()) {
            return allTables;
        }

        Set<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return allTables; // 매칭할 토큰이 없음 → 폴백
        }

        // 1) 테이블별 키워드 스코어링 (이름 > 설명 > 컬럼, 부분·양방향 매칭)
        List<Scored> scored = new ArrayList<>();
        for (TableSchema t : allTables) {
            int score = scoreTable(t, tokens);
            if (score > 0) {
                scored.add(new Scored(t, score));
            }
        }
        if (scored.isEmpty()) {
            log.info("Pre-filter: 매칭 0건 → 전체 {}개 테이블 폴백", allTables.size());
            return allTables; // 매칭 실패 → 전체 폴백(정확도 보존)
        }

        // 2) 실효 상한 = max(maxCandidates, ceil(전체 × ratio)) — 정확도 최우선(넉넉히 유지)
        int effectiveLimit = effectiveLimit(allTables.size());

        // 3) 점수 내림차순(동점은 원본 순서 유지) 후 상위 실효상한 개 선택
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        Set<String> selectedNames = new LinkedHashSet<>();
        for (Scored s : scored) {
            if (selectedNames.size() >= effectiveLimit) break;
            selectedNames.add(s.table.getTableName());
        }
        int scoredSelected = selectedNames.size();

        // 4) 관계 1-hop 이웃 무조건 포함(스코어 0인 브리지/코어 FK 테이블 누락 방지).
        //    recall 우선이므로 실효 상한을 넘어 전체 크기까지 허용한다.
        if (props.isExpandRelations() && relations != null && !relations.isEmpty()) {
            List<String> seeds = new ArrayList<>(selectedNames);
            for (String seed : seeds) {
                for (TableRelation r : relations) {
                    if (seed.equals(r.getFromTable())) selectedNames.add(r.getToTable());
                    if (seed.equals(r.getToTable())) selectedNames.add(r.getFromTable());
                }
            }
        }

        // 5) 원본 순서를 보존해 반환(결정적 출력)
        List<TableSchema> result = allTables.stream()
                .filter(t -> selectedNames.contains(t.getTableName()))
                .collect(Collectors.toList());

        log.info("Pre-filter: {}개 → {}개 후보 (scored {}개 + 관계이웃 {}개, 실효상한 {}, query 토큰 {}개)",
                allTables.size(), result.size(), scoredSelected, result.size() - scoredSelected,
                effectiveLimit, tokens.size());
        return result;
    }

    private int effectiveLimit(int total) {
        double ratio = Math.max(0.0, Math.min(1.0, props.getCandidateRatio()));
        int ratioBased = (int) Math.ceil(total * ratio);
        return Math.max(props.getMaxCandidates(), ratioBased);
    }

    private int scoreTable(TableSchema t, Set<String> tokens) {
        Set<String> nameToks = tokensOf(normalize(t.getTableName()));
        Set<String> descToks = tokensOf(normalize(t.getTableDescription()));
        Set<String> colToks = new LinkedHashSet<>();
        if (t.getColumns() != null) {
            for (ColumnDef c : t.getColumns()) {
                colToks.addAll(tokensOf(normalize(c.getColumnName())));
                colToks.addAll(tokensOf(normalize(c.getColumnComment())));
            }
        }

        int score = 0;
        for (String token : tokens) {
            if (matches(nameToks, token)) score += 3;
            if (matches(descToks, token)) score += 2;
            if (matches(colToks, token)) score += 1;
        }
        return score;
    }

    /** 부분·양방향 매칭: 완전일치 또는 어느 한쪽이 다른 쪽을 포함(3자 이상)하면 매칭으로 본다. */
    private boolean matches(Set<String> tableTokens, String queryToken) {
        for (String tt : tableTokens) {
            if (tt.equals(queryToken)) return true;
            if (queryToken.length() >= 3 && tt.contains(queryToken)) return true;
            if (tt.length() >= 3 && queryToken.contains(tt)) return true;
        }
        return false;
    }

    /**
     * 질의를 토큰 집합으로 분해한다. 유니코드 토큰 + 동의어 사전으로 확장한 영문 토큰을 함께 담는다.
     * 동의어는 질의 전체 부분일치로 적용되어 "지점별"처럼 조사가 붙은 형태도 매칭한다.
     */
    private Set<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        String lower = query.toLowerCase();

        Set<String> tokens = tokensOf(lower);

        Map<String, String> synonyms = props.getSynonyms();
        if (synonyms != null && !synonyms.isEmpty()) {
            for (Map.Entry<String, String> e : synonyms.entrySet()) {
                String key = e.getKey();
                if (key == null || key.isBlank()) continue;
                if (lower.contains(key.toLowerCase())) {
                    tokens.addAll(tokensOf(e.getValue().toLowerCase()));
                }
            }
        }
        return tokens;
    }

    /** 문자열을 2자 이상 토큰 집합으로 분해(1자 토큰은 노이즈가 커서 제외). */
    private Set<String> tokensOf(String text) {
        if (text == null || text.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(TOKEN_SPLIT.split(text))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 소문자화 + 언더스코어를 공백으로 치환해 식별자 토큰 매칭이 되도록 정규화. */
    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replace('_', ' ');
    }

    private record Scored(TableSchema table, int score) {
    }
}
