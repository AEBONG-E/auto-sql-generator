package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SqlPromptBuilderTest {

    private SqlPromptBuilder.Input fixtureInput() {
        String ddl = new DdlContextBuilder().build(Step2PromptFixture.tables(), Step2PromptFixture.relations());
        return new SqlPromptBuilder.Input(Step2PromptFixture.tables(), ddl, Step2PromptFixture.QUERY, SqlDialect.MYSQL);
    }

    @Test
    @DisplayName("MYSQL 방언의 시스템 프롬프트와 사용자 메시지가 golden 과 바이트 단위로 같다")
    void build_matchesGoldenBytes() {
        SqlPromptBuilder.Result result = SqlPromptBuilder.build(fixtureInput());

        assertThat(result.systemPrompt().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(Step2PromptFixture.golden(Step2PromptFixture.SYSTEM_GOLDEN).getBytes(StandardCharsets.UTF_8));
        assertThat(result.userMessage().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(Step2PromptFixture.golden(Step2PromptFixture.USER_GOLDEN).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("같은 입력에는 항상 같은 결과를 돌려준다 (상태 없음)")
    void build_isDeterministic() {
        assertThat(SqlPromptBuilder.build(fixtureInput())).isEqualTo(SqlPromptBuilder.build(fixtureInput()));
    }
}
