package com.autoerd.service;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * gemma4의 prompt_eval 토큰이 급증해 Step1 지연이 33~75초까지 커진다. 사용자 질의의 토큰과
 * 테이블명/설명/컬럼명을 매칭해 후보를 상위 N개로 좁혀 프롬프트 크기를 낮춘다.
 *
 * <p>안전 원칙(회귀 방지):
 * <ul>
 *   <li>소형 스키마({@code min-tables} 이하)에는 적용하지 않고 전체를 그대로 넘긴다 → 기존 동작 보존.</li>
 *   <li>매칭 후보가 0건이면 전체를 넘기는 폴백을 유지한다 → 정확도 보존.</li>
 *   <li>최종 선별은 여전히 LLM + {@code validNames} 서버측 필터가 담당한다. 본 필터는 앞단의 후보 축소일 뿐이다.</li>
 *   <li>관계(추론된 FK)로 1-hop 이웃 테이블을 후보에 포함해 JOIN 브리지 테이블 누락을 완화한다.</li>
 *   <li>{@code app.llm.prefilter.enabled=false}로 완전히 끌 수 있다.</li>
 * </ul>
 */
@Slf4j
@Component
public class TableCandidatePrefilter {

    /** 질의 토큰 분리: 유니코드 문자/숫자가 아닌 경계로 분리(한글·영문 혼용 대응). */
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** 필터 on/off. Ollama와 무관하며, 대형 스키마 성능 최적화 스위치. 기본 true. */
    @Value("${app.llm.prefilter.enabled:true}")
    private boolean enabled;

    /** 테이블 수가 이 값 이하이면 사전 축소를 적용하지 않고 전체를 넘긴다(소형 스키마 회귀 방지). */
    @Value("${app.llm.prefilter.min-tables:30}")
    private int minTables;

    /** Step1에 넘길 최대 후보 테이블 수. */
    @Value("${app.llm.prefilter.max-candidates:30}")
    private int maxCandidates;

    /**
     * 질의와 스키마를 받아 Step1에 넘길 후보 테이블을 축소해 반환한다.
     * 적용 조건을 만족하지 못하거나 매칭이 없으면 {@code allTables}를 그대로 반환한다.
     *
     * @param query     사용자 자연어 질의
     * @param allTables 전체 테이블(스키마 스냅샷)
     * @param relations 추론된 관계(없으면 빈 리스트 허용) — 1-hop 이웃 포함에 사용
     */
    public List<TableSchema> prefilter(String query, List<TableSchema> allTables, List<TableRelation> relations) {
        if (!enabled || allTables == null || allTables.size() <= minTables) {
            return allTables;
        }

        Set<String> tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return allTables; // 매칭할 토큰이 없음 → 폴백
        }

        // 1) 테이블별 키워드 스코어링 (이름 > 설명 > 컬럼)
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

        // 2) 점수 내림차순(동점은 원본 순서 유지) 후 상위 N개 선택
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        Set<String> selectedNames = new LinkedHashSet<>();
        for (Scored s : scored) {
            if (selectedNames.size() >= maxCandidates) break;
            selectedNames.add(s.table.getTableName());
        }

        // 3) 관계 1-hop 이웃 포함(JOIN 브리지 테이블 누락 완화), 상한 내에서만
        if (relations != null && !relations.isEmpty()) {
            List<String> seeds = new ArrayList<>(selectedNames);
            for (String seed : seeds) {
                if (selectedNames.size() >= maxCandidates) break;
                for (TableRelation r : relations) {
                    if (selectedNames.size() >= maxCandidates) break;
                    if (seed.equals(r.getFromTable())) selectedNames.add(r.getToTable());
                    if (seed.equals(r.getToTable())) selectedNames.add(r.getFromTable());
                }
            }
        }

        // 4) 원본 순서를 보존해 반환(결정적 출력)
        List<TableSchema> result = allTables.stream()
                .filter(t -> selectedNames.contains(t.getTableName()))
                .collect(Collectors.toList());

        log.info("Pre-filter: {}개 → {}개 후보로 축소 (query 토큰 {}개)",
                allTables.size(), result.size(), tokens.size());
        return result;
    }

    private int scoreTable(TableSchema t, Set<String> tokens) {
        String nameText = normalize(t.getTableName());
        String descText = normalize(t.getTableDescription());
        String colText = t.getColumns() == null ? "" : t.getColumns().stream()
                .map(this::columnText)
                .collect(Collectors.joining(" "));

        int score = 0;
        for (String token : tokens) {
            if (!nameText.isEmpty() && nameText.contains(token)) score += 3;
            if (!descText.isEmpty() && descText.contains(token)) score += 2;
            if (!colText.isEmpty() && colText.contains(token)) score += 1;
        }
        return score;
    }

    private String columnText(ColumnDef c) {
        String name = normalize(c.getColumnName());
        String comment = normalize(c.getColumnComment());
        return (name + " " + comment).trim();
    }

    private Set<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(TOKEN_SPLIT.split(query.toLowerCase()))
                .map(String::trim)
                .filter(s -> s.length() >= 2) // 1글자 토큰은 노이즈가 커서 제외
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
