package com.autoerd.infrastructure.llm;

import com.autoerd.model.TableSchema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Step2 SQL 생성 프롬프트(시스템 프롬프트, 사용자 메시지)를 조립한다.
 * 상태를 갖지 않는 순수 함수만 두며, 같은 입력에는 항상 같은 문자열을 돌려준다.
 * 프롬프트에 영향을 주는 값은 {@link Input} 으로 묶어 받는다. 이후 방언 등 입력이 늘면 Input 에 추가한다.
 */
final class SqlPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a MySQL SQL generator. Generate a single, performant MySQL query.

            SCHEMA RULES:
            - Use ONLY the table names listed in the "AVAILABLE TABLES" section.
            - Use ONLY column names that appear in the CREATE TABLE statement for each table.

            QUERY QUALITY RULES:
            - Prefer JOIN over subqueries; write flat queries the optimizer can handle efficiently.
            - Use inferred relationships (FK columns ending in _id) to determine JOIN keys.
            - Apply WHERE filters on indexed columns before aggregating.
            - For date ranges: WHERE col >= DATE_SUB(NOW(), INTERVAL N DAY).
            - For ranking: flat SELECT + JOIN + GROUP BY + ORDER BY + LIMIT (no nested aggregation).
            - Select only the columns needed. Use short table aliases.
            - End with a semicolon.

            OUTPUT: SQL query only. No explanation, no markdown, no code fences.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            AVAILABLE TABLES (use ONLY these):
            %s

            DDL + RELATIONSHIPS:
            %s

            Request: "%s"
            """;

    private SqlPromptBuilder() {
    }

    /**
     * 프롬프트 조립 입력.
     *
     * @param tables 프롬프트에 노출할 테이블 목록 (AVAILABLE TABLES)
     * @param ddl    DdlContextBuilder 가 만든 DDL 과 관계 요약
     * @param query  사용자 질의 원문
     */
    record Input(List<TableSchema> tables, String ddl, String query) {
    }

    /** 조립 결과. LLM 에 보낼 시스템 프롬프트와 사용자 메시지. */
    record Result(String systemPrompt, String userMessage) {
    }

    static Result build(Input input) {
        return new Result(SYSTEM_PROMPT, userMessage(input));
    }

    private static String userMessage(Input input) {
        return USER_MESSAGE_TEMPLATE.formatted(availableTables(input.tables()), input.ddl(), input.query());
    }

    private static String availableTables(List<TableSchema> tables) {
        return tables.stream()
                .map(s -> "  - " + s.getTableName()
                        + (s.getTableDescription() != null && !s.getTableDescription().isBlank()
                            ? " (" + s.getTableDescription() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }
}
