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

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .map(OllamaSqlGenerationAdapter::stripThinking)
                .filter(chunk -> !chunk.isEmpty());
    }

    private static String stripThinking(String chunk) {
        return chunk.replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("(?s)</?think>", "");
    }
}
