package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlTableResolverPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmSqlTableResolverAdapter implements SqlTableResolverPort {

    private static final String SYSTEM_PROMPT = """
            You are a database table selector. Your only job:
            given a list of tables and a user query, return which table names are needed.

            RULES:
            - Reply ONLY with table names from the provided list, comma-separated.
            - Do NOT add any table name that is not in the provided list.
            - Match by table description (in brackets), not by assumed conventional names.
            - If the query needs multiple tables, list all of them.
            - If nothing matches, reply with exactly: NONE
            """;

    private final ChatClient chatClient;

    @Override
    public List<String> resolveRelevantTableNames(SqlTableResolutionRequest request) {
        String tableList = String.join("\n", request.candidateTableNames());
        String userContent = """
                Database tables:
                %s

                User query: "%s"

                Which table names are needed? Reply with names only, comma-separated. If none match: NONE
                """.formatted(tableList, request.query());

        try {
            String response = chatClient.prompt()
                    .messages(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userContent)))
                    .call()
                    .content();

            response = stripThinking(response).trim();
            log.info("Table resolution response: [{}]", response);

            if (response.isBlank() || response.equalsIgnoreCase("NONE")) {
                return List.of();
            }

            Set<String> validNames = new HashSet<>(request.candidateTableNames());
            return Arrays.stream(response.split("[,\\n]+"))
                    .map(String::trim)
                    .map(s -> s.replaceAll("[^a-zA-Z0-9_]", ""))
                    .filter(s -> !s.isEmpty())
                    .filter(validNames::contains)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Table resolution failed: {}", e.getMessage());
            return List.of();
        }
    }

    private static String stripThinking(String text) {
        return text.replaceAll("(?s)<think>.*?</think>", "")
                   .replaceAll("(?s)</?think>", "");
    }
}
