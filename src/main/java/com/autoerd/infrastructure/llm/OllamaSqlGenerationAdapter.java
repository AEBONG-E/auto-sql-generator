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
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaSqlGenerationAdapter implements SqlGenerationPort {

    /** 세미콜론으로 끝나거나, 세미콜론 뒤에 '--' 주석만 붙은 줄. */
    private static final Pattern TERMINATED_LINE = Pattern.compile(".*;\\s*(--.*)?");

    private final ChatClient chatClient;
    private final DdlContextBuilder ddlContextBuilder;

    @Override
    public Flux<String> generate(SqlGenerationRequest request) {
        List<TableSchema> tables = request.metadataSnapshot().tables();
        List<TableRelation> relations = request.metadataSnapshot().relations();

        String ddl = ddlContextBuilder.build(tables, relations);

        SqlPromptBuilder.Result built = SqlPromptBuilder.build(new SqlPromptBuilder.Input(
                tables, ddl, request.query(), request.metadataSnapshot().dialect()));
        var prompt = new Prompt(List.of(new SystemMessage(built.systemPrompt()), new UserMessage(built.userMessage())));

        StringBuilder received = new StringBuilder();
        long[] startedAt = {System.currentTimeMillis()};

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .map(OllamaSqlGenerationAdapter::stripThinking)
                .filter(chunk -> !chunk.isEmpty())
                .doOnNext(received::append)
                .doOnSubscribe(s -> {
                    startedAt[0] = System.currentTimeMillis();
                    log.info("SQL 생성 스트림 시작: projectId={}, queryLength={}",
                            request.projectId(), request.query().length());
                })
                .doOnComplete(() -> log.info(
                        "SQL 생성 스트림 정상 종료(onComplete): projectId={}, {}자 수신, 세미콜론종료={}, elapsedMs={}",
                        request.projectId(), received.length(), endsWithStatementTerminator(received.toString()),
                        System.currentTimeMillis() - startedAt[0]))
                .doOnCancel(() -> log.warn(
                        "SQL 생성 스트림 취소(onCancel, 클라이언트/타임아웃 등으로 구독 중단 추정): projectId={}, {}자 수신, elapsedMs={}",
                        request.projectId(), received.length(), System.currentTimeMillis() - startedAt[0]))
                .doOnError(e -> log.error(
                        "SQL 생성 스트림 오류 종료(onError): projectId={}, {}자 수신, elapsedMs={}, cause={}",
                        request.projectId(), received.length(), System.currentTimeMillis() - startedAt[0], e.toString()))
                // 정상 완료(onComplete)되었으나 SQL이 세미콜론 없이 끊긴 경우 — LLM의 조기 EOS 방출 등으로
                // 스트림이 미완성 상태로 종료된 것으로 추정. 사용자에게 명시적으로 알려 재시도를 유도한다.
                .concatWith(Flux.defer(() -> {
                    String content = received.toString().trim();
                    if (!content.isEmpty() && !endsWithStatementTerminator(content)) {
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

    /**
     * 마지막 비주석 줄이 세미콜론으로 끝나는지 본다 (이슈 #18).
     * 빈 줄과 '--' 로 시작하는 줄은 건너뛰고, 같은 줄의 세미콜론 뒤에 붙은 '--' 주석은 허용한다.
     * 비주석 줄이 하나도 없으면 SQL 이 없으므로 false 를 돌려준다.
     */
    static boolean endsWithStatementTerminator(String content) {
        String[] lines = content.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("--")) {
                continue;
            }
            return TERMINATED_LINE.matcher(line).matches();
        }
        return false;
    }

    private static String stripThinking(String chunk) {
        return chunk.replaceAll("(?s)<think>.*?</think>", "")
                    .replaceAll("(?s)</?think>", "");
    }
}
