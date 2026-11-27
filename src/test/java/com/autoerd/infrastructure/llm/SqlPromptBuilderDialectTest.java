package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.service.DdlContextBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step2 프롬프트의 방언 블록, 인용된 테이블명, 값 규칙 (이슈 #18, 설계 5.2, 5.3, 5.5).
 */
class SqlPromptBuilderDialectTest {

    /** 방언별 금지 줄. 설계 5.3 표의 '금지' 행과 같다. */
    private static final Map<SqlDialect, String> FORBIDDEN_LINE = Map.of(
            SqlDialect.MSSQL, "- FORBIDDEN in SQL Server: LIMIT, NOW(), INTERVAL, DATE_SUB, IFNULL, backtick (`) quotes.",
            SqlDialect.MYSQL, "- FORBIDDEN in MySQL: TOP, square bracket [ ] quotes, GETDATE(), :: casts.",
            SqlDialect.POSTGRESQL,
            "- FORBIDDEN in PostgreSQL: TOP, square bracket [ ] quotes, backtick (`) quotes, DATE_SUB, IFNULL.");

    private static final Map<SqlDialect, String> DIALECT_HEADER = Map.of(
            SqlDialect.MSSQL, "DIALECT: Microsoft SQL Server (T-SQL)",
            SqlDialect.MYSQL, "DIALECT: MySQL",
            SqlDialect.POSTGRESQL, "DIALECT: PostgreSQL");

    /** 2단계까지 고정돼 있던 MySQL 전용 문구. 공통 부분에서 빠져야 한다. */
    private static final String LEGACY_DATE_RULE = "For date ranges: WHERE col >= DATE_SUB(NOW(), INTERVAL N DAY).";
    private static final String LEGACY_RANKING_RULE = "ORDER BY + LIMIT (no nested aggregation)";

    private static SqlPromptBuilder.Result build(SqlDialect dialect) {
        String ddl = new DdlContextBuilder().build(Step2PromptFixture.tables(), Step2PromptFixture.relations());
        return SqlPromptBuilder.build(
                new SqlPromptBuilder.Input(Step2PromptFixture.tables(), ddl, Step2PromptFixture.QUERY, dialect));
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("자기 방언 블록과 금지 줄만 들어가고 다른 방언의 블록과 금지 줄은 들어가지 않는다")
    void systemPrompt_containsOnlyOwnDialectBlock(SqlDialect dialect) {
        String system = build(dialect).systemPrompt();

        assertThat(system).contains(DIALECT_HEADER.get(dialect)).contains(FORBIDDEN_LINE.get(dialect));
        Arrays.stream(SqlDialect.values()).filter(other -> other != dialect).forEach(other -> {
            assertThat(system).doesNotContain(DIALECT_HEADER.get(other));
            assertThat(system).doesNotContain(FORBIDDEN_LINE.get(other));
        });
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("MySQL 고정 날짜/행 제한 문구가 공통 규칙에서 빠진다")
    void systemPrompt_dropsLegacyMysqlRules(SqlDialect dialect) {
        String system = build(dialect).systemPrompt();

        assertThat(system).doesNotContain(LEGACY_DATE_RULE).doesNotContain(LEGACY_RANKING_RULE);
    }

    @Test
    @DisplayName("MSSQL 프롬프트에는 MySQL 이라는 말과 MySQL 권장 문법이 없다")
    void mssql_hasNoMysqlWording() {
        String system = build(SqlDialect.MSSQL).systemPrompt();

        assertThat(system).startsWith("You are a SQL Server (T-SQL) SQL generator.");
        assertThat(system).doesNotContain("MySQL").doesNotContain("DATE_SUB(NOW()").doesNotContain("LIMIT N");
        assertThat(system).contains("DATEADD(MONTH, -3, SYSDATETIME())").contains("SELECT TOP (N)");
    }

    @Test
    @DisplayName("POSTGRESQL 프롬프트에는 MySQL 이라는 말과 T-SQL 권장 문법이 없다")
    void postgresql_hasNoMysqlOrTsqlWording() {
        String system = build(SqlDialect.POSTGRESQL).systemPrompt();

        assertThat(system).startsWith("You are a PostgreSQL SQL generator.");
        assertThat(system).doesNotContain("MySQL").doesNotContain("DATEADD").doesNotContain("SELECT TOP");
        assertThat(system).contains("NOW() - INTERVAL '3 months'");
    }

    @Test
    @DisplayName("MYSQL 프롬프트에는 T-SQL 권장 문법이 없다")
    void mysql_hasNoTsqlWording() {
        String system = build(SqlDialect.MYSQL).systemPrompt();

        assertThat(system).startsWith("You are a MySQL SQL generator.");
        assertThat(system).doesNotContain("DATEADD").doesNotContain("SELECT TOP").doesNotContain("SYSDATETIME");
        assertThat(system).contains("DATE_SUB(NOW(), INTERVAL 3 MONTH)");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("값 규칙: 근거 없는 코드값은 CONFIRM_VALUE 와 SQL 앞 주석, 근거 있는 값은 그대로 쓴다")
    void systemPrompt_hasValueRules(SqlDialect dialect) {
        String system = build(dialect).systemPrompt();

        assertThat(system).contains("VALUE RULES:")
                .contains("'<CONFIRM_VALUE>'")
                .contains("-- CONFIRM:")
                .contains("ABOVE the statement")
                .contains("column comment or table description")
                .contains("use it exactly as written");
    }

    @ParameterizedTest
    @EnumSource(SqlDialect.class)
    @DisplayName("테이블은 목록의 인용된 이름을 그대로, 컬럼은 예약어이거나 인용이 꼭 필요할 때만 인용하도록 지시한다")
    void systemPrompt_hasQuotingRules(SqlDialect dialect) {
        String system = build(dialect).systemPrompt();

        assertThat(system).contains("copy the quoted table name exactly as listed")
                .contains("Quote a column name only if it is a reserved word or cannot be written unquoted")
                .contains("mixed case in PostgreSQL");
    }

    @Test
    @DisplayName("AVAILABLE TABLES 에 방언별로 인용된 테이블명을 넣는다 (설명은 그대로)")
    void userMessage_listsQuotedTableNames() {
        assertThat(build(SqlDialect.MSSQL).userMessage())
                .contains("  - [shop].[group] (고객 그룹)\n  - [shop].[purchase_order]\n  - [tb_code]\n");
        assertThat(build(SqlDialect.MYSQL).userMessage())
                .contains("  - `shop`.`group` (고객 그룹)\n  - `shop`.`purchase_order`\n  - `tb_code`\n");
        assertThat(build(SqlDialect.POSTGRESQL).userMessage())
                .contains("  - \"shop\".\"group\" (고객 그룹)\n  - \"shop\".\"purchase_order\"\n  - \"tb_code\"\n");
    }

    @Test
    @DisplayName("DDL 과 질의 원문은 방언과 관계없이 그대로 들어간다")
    void userMessage_keepsDdlAndQuery() {
        String user = build(SqlDialect.MSSQL).userMessage();

        assertThat(user).contains("CREATE TABLE shop.group (").contains("Request: \"" + Step2PromptFixture.QUERY + "\"");
    }
}
