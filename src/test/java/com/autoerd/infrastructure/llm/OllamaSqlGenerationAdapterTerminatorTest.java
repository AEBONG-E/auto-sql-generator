package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.model.TableSchema;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 응답 종료 판정: 마지막 비주석 줄이 세미콜론으로 끝나는지로 본다 (이슈 #18).
 */
class OllamaSqlGenerationAdapterTerminatorTest {

    private static final String INTERRUPTED_NOTICE = "응답이 예기치 않게 중단되었습니다";

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final OllamaSqlGenerationAdapter adapter =
            new OllamaSqlGenerationAdapter(chatClient, new DdlContextBuilder());
    private final SqlGenerationRequest request = new SqlGenerationRequest(5L, "질의", new SqlMetadataSnapshot(
            List.of(TableSchema.builder().tableName("tb_stock").columns(List.of()).build()), List.of()));

    private String generate(String... chunks) {
        when(chatClient.prompt(any(Prompt.class)).stream().content()).thenReturn(Flux.just(chunks));
        return String.join("", adapter.generate(request).collectList().block());
    }

    @Test
    @DisplayName("세미콜론 뒤에 -- 주석 줄이 붙어도 중단 경고를 붙이지 않는다")
    void trailingCommentLine_afterSemicolon_noWarning() {
        String body = generate("SELECT 1\nFROM t;\n", "-- CONFIRM: status 값 확인 필요\n");

        assertThat(body).doesNotContain(INTERRUPTED_NOTICE);
    }

    @Test
    @DisplayName("세미콜론과 같은 줄 뒤에 붙은 -- 주석도 종료로 본다")
    void inlineCommentAfterSemicolon_noWarning() {
        String body = generate("SELECT 1 FROM t; -- 확인 필요");

        assertThat(body).doesNotContain(INTERRUPTED_NOTICE);
    }

    @Test
    @DisplayName("SQL 앞의 CONFIRM 주석과 세미콜론 종료는 정상으로 본다")
    void leadingCommentAndSemicolon_noWarning() {
        String body = generate("-- CONFIRM: 값 확인 필요\nSELECT 1 FROM t\nWHERE s = '<CONFIRM_VALUE>';\n\n");

        assertThat(body).doesNotContain(INTERRUPTED_NOTICE);
    }

    @Test
    @DisplayName("마지막 비주석 줄이 세미콜론 없이 끝나면 기존대로 중단 경고를 붙인다")
    void lastCodeLineWithoutSemicolon_warns() {
        String body = generate("SELECT 1\nFROM t\n-- 주석;\n");

        assertThat(body).contains(INTERRUPTED_NOTICE);
    }

    @Test
    @DisplayName("주석만 있는 응답은 SQL 이 없으므로 중단 경고를 붙인다")
    void commentOnly_warns() {
        String body = generate("-- 생성 실패\n");

        assertThat(body).contains(INTERRUPTED_NOTICE);
    }
}
