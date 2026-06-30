package com.autoerd.service;

import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaSqlService {

    // ── Step 1: 테이블 매핑 전용 프롬프트 ──────────────────────────────────────
    // 단순 분류 작업 — SQL 규칙을 섞으면 오히려 혼란을 주므로 완전히 분리
    private static final String TABLE_RESOLUTION_SYSTEM = """
            You are a database table selector. Your only job:
            given a list of tables and a user query, return which table names are needed.

            RULES:
            - Reply ONLY with table names from the provided list, comma-separated.
            - Do NOT add any table name that is not in the provided list.
            - Match by table description (in brackets), not by assumed conventional names.
            - If the query needs multiple tables, list all of them.
            - If nothing matches, reply with exactly: NONE
            """;

    // ── Step 2: SQL 생성 전용 프롬프트 ────────────────────────────────────────
    // 이미 검증된 테이블만 컨텍스트에 있으므로 쿼리 품질에만 집중
    private static final String SQL_GENERATION_SYSTEM = """
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

    private final ChatClient chatClient;
    private final DdlContextBuilder ddlContextBuilder;
    private final SchemaSessionStore schemaSessionStore;
    private final SchemaAnalysisService schemaAnalysisService;

    public Flux<String> generateSqlStream(String userQuery) {
        List<TableSchema> allSchemas = schemaSessionStore.get();
        if (allSchemas.isEmpty()) {
            return Flux.just("-- 스키마 정보가 없습니다. 먼저 엑셀 파일을 업로드해 주세요.");
        }

        // ── Step 1: 테이블 매핑 (동기 호출) ──────────────────────────────────
        List<TableSchema> relevantSchemas = resolveRelevantTables(allSchemas, userQuery);
        if (relevantSchemas.isEmpty()) {
            return Flux.just("-- insufficient_schema: 요청에 해당하는 테이블을 스키마에서 찾을 수 없습니다.\n"
                    + "-- 업로드된 스키마에 관련 테이블이 있는지 확인해 주세요.");
        }

        log.info("Resolved tables [{}] for query: {}",
                relevantSchemas.stream().map(TableSchema::getTableName).collect(Collectors.joining(", ")),
                userQuery);

        // ── Step 2: 검증된 테이블로만 SQL 생성 (스트리밍) ─────────────────────
        List<TableRelation> relations = schemaAnalysisService.inferRelations(relevantSchemas);
        String ddl = ddlContextBuilder.build(relevantSchemas, relations);

        String availableTables = relevantSchemas.stream()
                .map(s -> "  - " + s.getTableName()
                        + (s.getTableDescription() != null && !s.getTableDescription().isBlank()
                            ? " (" + s.getTableDescription() + ")" : ""))
                .collect(Collectors.joining("\n"));

        String userContent = """
                AVAILABLE TABLES (use ONLY these):
                %s

                DDL + RELATIONSHIPS:
                %s

                Request: "%s"
                """.formatted(availableTables, ddl, userQuery);

        var prompt = new Prompt(List.of(new SystemMessage(SQL_GENERATION_SYSTEM), new UserMessage(userContent)));

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .map(OllamaSqlService::stripThinkingTokens)
                .filter(chunk -> !chunk.isEmpty());
    }

    /**
     * Step 1: LLM에게 "어떤 테이블이 필요한가"만 물어본다.
     * 응답에서 실제 스키마에 존재하는 테이블명만 추출 — LLM이 잘못된 이름을 반환해도 차단됨.
     */
    private List<TableSchema> resolveRelevantTables(List<TableSchema> schemas, String userQuery) {
        String tableList = schemas.stream()
                .map(s -> s.getTableName()
                        + (s.getTableDescription() != null && !s.getTableDescription().isBlank()
                            ? " [" + s.getTableDescription() + "]" : ""))
                .collect(Collectors.joining("\n"));

        String resolutionContent = """
                Database tables:
                %s

                User query: "%s"

                Which table names are needed? Reply with names only, comma-separated. If none match: NONE
                """.formatted(tableList, userQuery);

        try {
            String response = chatClient.prompt()
                    .messages(List.of(
                            new SystemMessage(TABLE_RESOLUTION_SYSTEM),
                            new UserMessage(resolutionContent)))
                    .call()
                    .content();

            response = stripThinkingTokens(response).trim();
            log.info("Table resolution response: [{}]", response);

            if (response.isBlank() || response.equalsIgnoreCase("NONE")) {
                log.warn("Table resolution returned NONE for: {}", userQuery);
                return List.of();
            }

            // 실제 스키마에 존재하는 테이블명만 허용 — LLM 환각 차단
            Set<String> validNames = schemas.stream()
                    .map(TableSchema::getTableName)
                    .collect(Collectors.toSet());

            List<String> resolved = Arrays.stream(response.split("[,\\n]+"))
                    .map(String::trim)
                    .map(s -> s.replaceAll("[^a-zA-Z0-9_]", ""))
                    .filter(s -> !s.isEmpty())
                    .filter(validNames::contains)
                    .distinct()
                    .collect(Collectors.toList());

            if (resolved.isEmpty()) {
                log.warn("Table resolution returned names not in schema. Raw response: [{}]", response);
                return List.of();
            }

            Set<String> resolvedSet = new HashSet<>(resolved);
            return schemas.stream()
                    .filter(s -> resolvedSet.contains(s.getTableName()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Table resolution call failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String stripThinkingTokens(String chunk) {
        return chunk.replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("(?s)</?think>", "");
    }
}
