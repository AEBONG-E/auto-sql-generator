package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.model.TableSchema;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OllamaSqlGenerationAdapterLogTest {

    private static final String QUERY = "LOGCHECK_질의원문_지점별_재고현황";

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final OllamaSqlGenerationAdapter adapter =
            new OllamaSqlGenerationAdapter(chatClient, new DdlContextBuilder());
    private final SqlGenerationRequest request = new SqlGenerationRequest(3L, QUERY, new SqlMetadataSnapshot(
            List.of(TableSchema.builder().tableName("tb_stock").columns(List.of()).build()), List.of()));

    @Test
    @DisplayName("스트림 시작/종료 로그는 projectId, 질의 길이, 소요 시간만 남기고 질의 원문과 프롬프트는 남기지 않는다")
    void generate_success_logsMetadataOnly(CapturedOutput output) {
        when(chatClient.prompt(any(Prompt.class)).stream().content()).thenReturn(Flux.just("SELECT 1;"));

        adapter.generate(request).blockLast();

        assertThat(output).contains("SQL 생성 스트림 시작: projectId=3, queryLength=" + QUERY.length());
        assertThat(output).containsPattern("SQL 생성 스트림 정상 종료\\(onComplete\\): projectId=3, .*elapsedMs=\\d+");
        assertThat(output).doesNotContain(QUERY);
        assertThat(output).doesNotContain("AVAILABLE TABLES");
    }

    @Test
    @DisplayName("스트림 오류 로그는 projectId, 소요 시간, 오류 종류를 남기고 질의 원문은 남기지 않는다")
    void generate_error_logsCauseWithoutQuery(CapturedOutput output) {
        when(chatClient.prompt(any(Prompt.class)).stream().content())
                .thenReturn(Flux.error(new IllegalStateException("I/O error: Connection refused")));

        String body = String.join("", adapter.generate(request).collectList().block());

        assertThat(body).contains("SQL 생성 중 오류가 발생했습니다");
        assertThat(output).containsPattern(
                "SQL 생성 스트림 오류 종료\\(onError\\): projectId=3, .*elapsedMs=\\d+, cause=java.lang.IllegalStateException");
        assertThat(output).doesNotContain(QUERY);
    }
}
