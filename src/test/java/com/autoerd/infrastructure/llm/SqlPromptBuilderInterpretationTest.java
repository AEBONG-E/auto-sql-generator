package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step2 프롬프트의 해석 표시, 상태 판단, 목록 조회 기본값, 출력 전 자기 점검 규칙 (이슈 #23).
 * 방언과 관계없이 같은 문구가 들어가고, #18 의 값 규칙과 방언 블록 뒤에 온다.
 */
class SqlPromptBuilderInterpretationTest {

    private static String systemPrompt(SqlDialect dialect) {
        String ddl = new DdlContextBuilder().build(Step2PromptFixture.tables(), Step2PromptFixture.relations());
        return SqlPromptBuilder.build(new SqlPromptBuilder.Input(
                Step2PromptFixture.tables(), ddl, Step2PromptFixture.QUERY, dialect)).systemPrompt();
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("해석이 갈리면 SQL 앞에 '-- 해석:' 한 줄을 쓰고, 출력 형식도 그 주석을 허용한다")
    void hasInterpretationLine(SqlDialect dialect) {
        assertThat(systemPrompt(dialect))
                .contains("INTERPRETATION RULES:")
                .contains("state it in ONE line ABOVE the statement: -- 해석: <chosen reading>.")
                .contains("Omit it when the request is unambiguous.")
                .contains("OUTPUT: SQL query only, optionally preceded by -- 해석 and -- CONFIRM comments.");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("상태는 상태 컬럼 값으로 판단하고, NULL 가능 날짜 컬럼으로 추정하지 않으며, 근거가 없으면 CONFIRM 을 쓴다")
    void hasStatusRules(SqlDialect dialect) {
        assertThat(systemPrompt(dialect))
                .contains("ONLY by comparing the status column (*_status, *_state, *_stat) with a value")
                .contains("A nullable date column (e.g. cancelled_at) tells WHEN, not WHETHER.")
                .contains("Never use it with IS NULL or IS NOT NULL to decide a state; use it only for a period filter.")
                .contains("If the state value is not documented, write status_col = '<CONFIRM_VALUE>' (or <>) "
                        + "with the -- CONFIRM comment from the VALUE RULES.")
                .contains("no state decided by a date IS NULL?");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("삭제여부 성격 컬럼이 있으면 삭제 행을 기본 제외한다")
    void hasSoftDeleteRule(SqlDialect dialect) {
        assertThat(systemPrompt(dialect))
                .contains("If a table has a soft-delete column (e.g. del_yn, is_deleted, deleted_flag), "
                        + "exclude deleted rows by default.");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("목록 조회 기본값: 대표 컬럼, 기간 컬럼 내림차순, 방언 행 제한, 반열린 기간 조건")
    void hasListQueryDefaults(SqlDialect dialect) {
        assertThat(systemPrompt(dialect))
                .contains("LIST QUERY DEFAULTS")
                .contains("Select representative columns (keys, name, status, main date, amount), never SELECT *.")
                .contains("ORDER BY the period or main date column DESC, and limit rows with the dialect row limit")
                .contains("Period filter: date_col >= start AND date_col < end");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("출력 전 자기 점검 목록이 마지막 OUTPUT 줄 바로 앞에 온다")
    void hasSelfCheckBeforeOutput(SqlDialect dialect) {
        String system = systemPrompt(dialect);

        assertThat(system).contains("SELF-CHECK before output: deleted rows excluded?")
                .contains("no FORBIDDEN token of this dialect?");
        assertThat(system.indexOf("SELF-CHECK")).isLessThan(system.indexOf("OUTPUT: SQL query only"));
        assertThat(system.indexOf("DIALECT:")).isLessThan(system.indexOf("INTERPRETATION RULES:"));
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("공통 규칙은 특정 방언의 행 제한 문법을 직접 쓰지 않는다 (방언 블록에만 둔다)")
    void commonRulesStayDialectNeutral(SqlDialect dialect) {
        String system = systemPrompt(dialect);
        String common = system.substring(system.indexOf("QUERY QUALITY RULES:"));

        assertThat(common).doesNotContain("TOP (").doesNotContain("LIMIT ").doesNotContain("FETCH NEXT");
    }
}
