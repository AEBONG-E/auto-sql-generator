package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.port.SqlGenerationPort;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import com.autoerd.service.DdlContextBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaSqlGenerationAdapter implements SqlGenerationPort {

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

    private final ChatClient chatClient;
    private final DdlContextBuilder ddlContextBuilder;

    @Override
    public Flux<String> generate(SqlGenerationRequest request) {
        List<TableSchema> tables = request.metadataSnapshot().tables();
        List<TableRelation> relations = request.metadataSnapshot().relations();

        String ddl = ddlContextBuilder.build(tables, relations);

        String availableTables = tables.stream()
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
                """.formatted(availableTables, ddl, request.query());

        var prompt = new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userContent)));

        StringBuilder received = new StringBuilder();

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .map(OllamaSqlGenerationAdapter::stripThinking)
                .filter(chunk -> !chunk.isEmpty())
                .doOnNext(received::append)
                .doOnSubscribe(s -> log.info("SQL 생성 스트림 시작: projectId={}, query={}",
                        request.projectId(), request.query()))
                .doOnComplete(() -> log.info(
                        "SQL 생성 스트림 정상 종료(onComplete): projectId={}, {}자 수신, 세미콜론종료={}",
                        request.projectId(), received.length(), received.toString().trim().endsWith(";")))
                .doOnCancel(() -> log.warn(
                        "SQL 생성 스트림 취소(onCancel, 클라이언트/타임아웃 등으로 구독 중단 추정): projectId={}, {}자 수신",
                        request.projectId(), received.length()))
                .doOnError(e -> log.error(
                        "SQL 생성 스트림 오류 종료(onError): projectId={}, {}자 수신, cause={}",
                        request.projectId(), received.length(), e.toString()))
                // 정상 완료(onComplete)되었으나 SQL이 세미콜론 없이 끊긴 경우 — LLM의 조기 EOS 방출 등으로
                // 스트림이 미완성 상태로 종료된 것으로 추정. 사용자에게 명시적으로 알려 재시도를 유도한다.
                .concatWith(Flux.defer(() -> {
                    String content = received.toString().trim();
                    if (!content.isEmpty() && !content.endsWith(";")) {
                        log.warn("SQL 생성 응답이 세미콜론 없이 종료됨 — 조기 종료로 추정: projectId={}, {}자",
                                request.projectId(), received.length());
                        return Flux.just("\n-- ⚠ 응답이 예기치 않게 중단되었습니다. 다시 시도해 주세요.");
                    }
                    return Flux.empty();
                }))
                .onErrorResume(e -> {
                    log.error("SQL 생성 중 예외 발생: projectId={}", request.projectId(), e);
                    return Flux.just("-- ⚠ SQL 생성 중 오류가 발생했습니다. 다시 시도해 주세요.");
                });
    }

    private static String stripThinking(String chunk) {
        return chunk.replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("(?s)</?think>", "");
    }
}
