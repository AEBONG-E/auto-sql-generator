package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaSqlGenerationAdapterPromptTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final OllamaSqlGenerationAdapter adapter =
            new OllamaSqlGenerationAdapter(chatClient, new DdlContextBuilder());

    @Test
    @DisplayName("MYSQL 방언일 때 Step2 LLM 에 보내는 시스템 프롬프트와 사용자 메시지가 golden 과 바이트 단위로 같다")
    void generate_sendsPromptIdenticalToGolden() {
        when(chatClient.prompt(any(Prompt.class)).stream().content()).thenReturn(Flux.just("SELECT 1;"));

        adapter.generate(Step2PromptFixture.request()).blockLast();

        List<Message> messages = capturedPrompt().getInstructions();
        assertThat(messages).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(bytes(messages.get(0).getText()))
                .isEqualTo(bytes(Step2PromptFixture.golden(Step2PromptFixture.SYSTEM_GOLDEN)));
        assertThat(bytes(messages.get(1).getText()))
                .isEqualTo(bytes(Step2PromptFixture.golden(Step2PromptFixture.USER_GOLDEN)));
    }

    @Test
    @DisplayName("요청 스냅샷의 방언(MSSQL)으로 시스템 프롬프트와 테이블명 인용을 만든다")
    void generate_usesSnapshotDialect() {
        when(chatClient.prompt(any(Prompt.class)).stream().content()).thenReturn(Flux.just("SELECT 1;"));
        SqlGenerationRequest mssql = new SqlGenerationRequest(7L, Step2PromptFixture.QUERY, new SqlMetadataSnapshot(
                Step2PromptFixture.tables(), Step2PromptFixture.relations(), SqlDialect.MSSQL));

        adapter.generate(mssql).blockLast();

        List<Message> messages = capturedPrompt().getInstructions();
        assertThat(messages.get(0).getText()).contains("DIALECT: Microsoft SQL Server (T-SQL)").doesNotContain("MySQL");
        assertThat(messages.get(1).getText()).contains("  - [member].[user] (회원)");
    }

    private Prompt capturedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient, atLeastOnce()).prompt(captor.capture());
        // deep stub 설정 시 prompt(any()) 호출이 null 인자로 한 번 기록되므로 실제 호출만 고른다.
        return captor.getAllValues().stream().filter(Objects::nonNull).reduce((first, last) -> last).orElseThrow();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
