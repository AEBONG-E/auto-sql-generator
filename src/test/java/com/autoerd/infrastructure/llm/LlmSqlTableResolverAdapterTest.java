package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LlmSqlTableResolverAdapterTest {

    private ChatClient chatClient;
    private LlmSqlTableResolverAdapter adapter;
    private final SqlTableResolutionRequest request =
            new SqlTableResolutionRequest(1L, "주문 목록", List.of("orders", "members"));

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        adapter = new LlmSqlTableResolverAdapter(chatClient);
    }

    @Test
    void LLM_호출이_실패하면_빈_목록이_아니라_SqlTableResolutionException을_던진다() {
        RuntimeException cause = new RuntimeException("I/O error: Connection refused");
        when(chatClient.prompt().messages(anyList()).call().content()).thenThrow(cause);

        assertThatThrownBy(() -> adapter.resolveRelevantTableNames(request))
                .isInstanceOf(SqlTableResolutionException.class)
                .hasCause(cause);
    }

    @Test
    void LLM이_NONE을_응답하면_빈_목록을_반환한다() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("NONE");

        assertThat(adapter.resolveRelevantTableNames(request)).isEmpty();
    }

    @Test
    @DisplayName("설명이 있는 후보는 'name [description]' 형식으로 Step1 프롬프트에 포함된다")
    void resolve_withDescription_includesDescriptionInPrompt() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("tb_order");
        SqlTableResolutionRequest request = new SqlTableResolutionRequest(
                1L, "주문 목록", List.of("tb_order", "tb_member"),
                Map.of("tb_order", "주문", "tb_member", "회원"));

        List<String> result = adapter.resolveRelevantTableNames(request);

        String userContent = captureUserContent();
        assertThat(userContent).contains("tb_order [주문]\ntb_member [회원]");
        assertThat(result).containsExactly("tb_order");
    }

    @Test
    @DisplayName("설명이 없거나 공백인 후보는 이름만 Step1 프롬프트에 포함된다")
    void resolve_withoutDescription_includesNameOnly() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("tb_log, tb_code, tb_unknown");
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("tb_code", "   ");
        SqlTableResolutionRequest request = new SqlTableResolutionRequest(
                1L, "로그", List.of("tb_log", "tb_code"), descriptions);

        List<String> result = adapter.resolveRelevantTableNames(request);

        String userContent = captureUserContent();
        assertThat(userContent).contains("tb_log\ntb_code\n");
        assertThat(userContent).doesNotContain("[");
        assertThat(result).containsExactly("tb_log", "tb_code");
    }

    @SuppressWarnings("unchecked")
    private String captureUserContent() {
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt(), atLeastOnce()).messages(captor.capture());
        return captor.getValue().stream()
                .filter(UserMessage.class::isInstance)
                .map(Message::getText)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Step1 LLM 호출 실패 로그에 질의 원문과 프롬프트가 남지 않는다")
    void resolve_failure_doesNotLogQueryOrPrompt(CapturedOutput output) {
        when(chatClient.prompt().messages(anyList()).call().content())
                .thenThrow(new RuntimeException("I/O error: Connection refused"));

        assertThatThrownBy(() -> adapter.resolveRelevantTableNames(request))
                .isInstanceOf(SqlTableResolutionException.class);

        assertThat(output).contains("Table resolution LLM call failed: type=java.lang.RuntimeException");
        assertThat(output).doesNotContain("주문 목록");
        assertThat(output).doesNotContain("Database tables:");
    }
}
