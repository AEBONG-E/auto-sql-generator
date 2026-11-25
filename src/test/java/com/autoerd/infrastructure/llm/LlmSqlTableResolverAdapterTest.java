package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmSqlTableResolverAdapterTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final LlmSqlTableResolverAdapter adapter = new LlmSqlTableResolverAdapter(chatClient);
    private final SqlTableResolutionRequest request =
            new SqlTableResolutionRequest(1L, "주문 목록", List.of("orders", "members"));

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
}
