package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.port.SqlIdentifierQuoter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialectSqlIdentifierQuoterTest {

    private static SqlIdentifierQuoter quoter(SqlDialect dialect) {
        return DialectSqlIdentifierQuoter.of(dialect);
    }

    @Test
    @DisplayName("MSSQL 은 대괄호로 schema.table 을 각각 인용한다")
    void mssql_quotesWithBrackets() {
        assertThat(quoter(SqlDialect.MSSQL).quoteTableName("shop.group")).isEqualTo("[shop].[group]");
        assertThat(quoter(SqlDialect.MSSQL).quoteIdentifier("group")).isEqualTo("[group]");
    }

    @Test
    @DisplayName("MYSQL 은 백틱으로 schema.table 을 각각 인용한다")
    void mysql_quotesWithBackticks() {
        assertThat(quoter(SqlDialect.MYSQL).quoteTableName("shop.group")).isEqualTo("`shop`.`group`");
        assertThat(quoter(SqlDialect.MYSQL).quoteIdentifier("group")).isEqualTo("`group`");
    }

    @Test
    @DisplayName("POSTGRESQL 은 큰따옴표로 schema.table 을 각각 인용한다")
    void postgresql_quotesWithDoubleQuotes() {
        assertThat(quoter(SqlDialect.POSTGRESQL).quoteTableName("shop.group")).isEqualTo("\"shop\".\"group\"");
        assertThat(quoter(SqlDialect.POSTGRESQL).quoteIdentifier("group")).isEqualTo("\"group\"");
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "MSSQL      | tb_code          | [tb_code]",
            "MSSQL      | shop.sales.item | [shop].[sales.item]",
            "MYSQL      | shop.sales.item | `shop`.`sales.item`",
            "POSTGRESQL | shop.sales.item | '\"shop\".\"sales.item\"'",
    })
    @DisplayName("schema.table 은 첫 '.' 기준으로 나누고, 점이 없으면 이름 하나로 인용한다")
    void quoteTableName_splitsOnFirstDot(SqlDialect dialect, String raw, String expected) {
        assertThat(quoter(dialect).quoteTableName(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("이름 안의 닫는 기호는 이중화한다")
    void closingQuote_isDoubled() {
        assertThat(quoter(SqlDialect.MSSQL).quoteIdentifier("a]b")).isEqualTo("[a]]b]");
        assertThat(quoter(SqlDialect.MSSQL).quoteIdentifier("a[b")).isEqualTo("[a[b]");
        assertThat(quoter(SqlDialect.MYSQL).quoteIdentifier("a`b")).isEqualTo("`a``b`");
        assertThat(quoter(SqlDialect.POSTGRESQL).quoteIdentifier("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(quoter(SqlDialect.MSSQL).quoteTableName("s]x.t]y")).isEqualTo("[s]]x].[t]]y]");
    }

    @Test
    @DisplayName("schema 가 비어 있으면 object 이름만 인용한다")
    void quoteQualifiedName_blankSchema_quotesObjectOnly() {
        assertThat(quoter(SqlDialect.MSSQL).quoteQualifiedName(null, "group")).isEqualTo("[group]");
        assertThat(quoter(SqlDialect.MSSQL).quoteQualifiedName(" ", "group")).isEqualTo("[group]");
        assertThat(quoter(SqlDialect.MSSQL).quoteQualifiedName("shop", "group")).isEqualTo("[shop].[group]");
    }

    @Test
    @DisplayName("빈 식별자는 거부한다")
    void blankIdentifier_isRejected() {
        assertThatThrownBy(() -> quoter(SqlDialect.MSSQL).quoteIdentifier(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> quoter(SqlDialect.MSSQL).quoteIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
