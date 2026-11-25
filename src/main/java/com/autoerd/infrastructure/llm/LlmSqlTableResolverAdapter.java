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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            log.info("Table resolution response: [{}]", response);

            if (response.isBlank() || response.equalsIgnoreCase("NONE")) {
                return List.of();
            }

            return mapToCandidateNames(response, request.candidateTableNames());

        } catch (Exception e) {
            // LLM 호출 실패(미기동, 타임아웃 등)를 "관련 테이블 없음"(빈 목록)과 구분하기 위해 예외로 전파한다.
            // 프롬프트나 질의 원문은 남기지 않고 예외 종류와 메시지 요약만 기록한다.
            Throwable root = rootCause(e);
            log.error("Table resolution LLM call failed: type={}, rootType={}, message={}",
                    e.getClass().getName(), root.getClass().getName(), summarize(e.getMessage()));
            throw new SqlTableResolutionException("Table resolution LLM call failed", e);
        }
    }

    /**
     * LLM 응답의 각 이름을 후보 테이블명(원래 표기)으로 매핑한다.
     * 대소문자를 구분하지 않고, 이름을 감싼 따옴표/백틱/대괄호를 무시하며,
     * schema.table 형식과 테이블명만 있는 형식을 모두 인식한다.
     * 스키마 없이 답한 이름이 여러 스키마에 있으면 일치하는 후보를 모두 포함한다 (recall 우선).
     */
    private static List<String> mapToCandidateNames(String response, List<String> candidateNames) {
        Map<String, List<String>> byFullName = new HashMap<>();
        Map<String, List<String>> byTableName = new HashMap<>();
        for (String name : candidateNames) {
            String key = name.toLowerCase(Locale.ROOT);
            byFullName.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
            byTableName.computeIfAbsent(tablePart(key), k -> new ArrayList<>()).add(name);
        }

        Set<String> selected = new LinkedHashSet<>();
        for (String token : response.split("[,\\n]+")) {
            String key = normalizeAnswerName(token);
            if (key.isEmpty()) {
                continue;
            }
            List<String> matches = byFullName.get(key);
            if (matches == null) {
                // schema.table 로 답했는데 정의서는 테이블명만 있는 경우 -> 테이블명 후보와 비교
                // 테이블명만 답했는데 정의서는 schema.table 인 경우 -> 테이블 부분이 같은 후보 전부
                matches = key.contains(".") ? byFullName.get(tablePart(key)) : byTableName.get(key);
            }
            if (matches != null) {
                selected.addAll(matches);
            }
        }
        return new ArrayList<>(selected);
    }

    private static String normalizeAnswerName(String token) {
        return token.replaceAll("[\"'`\\[\\]]", "")
                .replaceAll("\\s*\\.\\s*", ".")
                .replaceAll("^[^\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_]+$", "")
                .toLowerCase(Locale.ROOT);
    }

    private static String tablePart(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
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
