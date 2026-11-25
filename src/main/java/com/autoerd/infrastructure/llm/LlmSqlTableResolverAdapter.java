package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
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

    private static final int MAX_LOG_MESSAGE_LENGTH = 200;

    private final ChatClient chatClient;

    @Override
    public List<String> resolveRelevantTableNames(SqlTableResolutionRequest request) {
        String tableList = request.candidateTableNames().stream()
                .map(name -> formatCandidate(name, request.candidateTableDescriptions().get(name)))
                .collect(Collectors.joining("\n"));
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
            log.debug("Table resolution response: [{}]", response);

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
            // LLM 호출 실패(미기동, 타임아웃 등)를 "관련 테이블 없음"(빈 목록)과 구분하기 위해 예외로 전파한다.
            // 프롬프트나 질의 원문은 남기지 않고 예외 종류와 메시지 요약만 기록한다.
            Throwable root = rootCause(e);
            log.error("Table resolution LLM call failed: type={}, rootType={}, message={}",
                    e.getClass().getName(), root.getClass().getName(), summarize(e.getMessage()));
            throw new SqlTableResolutionException("Table resolution LLM call failed", e);
        }
    }

    private static Throwable rootCause(Throwable e) {
        Throwable current = e;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String summarize(String message) {
        if (message == null) {
            return "";
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() > MAX_LOG_MESSAGE_LENGTH
                ? oneLine.substring(0, MAX_LOG_MESSAGE_LENGTH) + "..."
                : oneLine;
    }

    // 레거시 OllamaSqlService.resolveRelevantTables 와 같은 "name [description]" 형식
    private static String formatCandidate(String name, String description) {
        return description != null && !description.isBlank()
                ? name + " [" + description + "]"
                : name;
    }

    private static String stripThinking(String text) {
        return text.replaceAll("(?s)<think>.*?</think>", "")
                   .replaceAll("(?s)</?think>", "");
    }
}
