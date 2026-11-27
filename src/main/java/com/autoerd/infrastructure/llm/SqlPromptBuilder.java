package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.port.SqlIdentifierQuoter;
import com.autoerd.model.TableSchema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Step2 SQL 생성 프롬프트(시스템 프롬프트, 사용자 메시지)를 조립한다.
 * 상태를 갖지 않는 순수 함수만 두며, 같은 입력에는 항상 같은 문자열을 돌려준다.
 * 프롬프트에 영향을 주는 값은 {@link Input} 으로 묶어 받는다.
 * 방언별 규칙 문구는 이 클래스의 text block 상수로 둔다 (이슈 #18, 설계 5.3, 5.4, 5.5).
 * 해석 표시, 상태 판단, 목록 조회 기본값, 출력 전 자기 점검 규칙은 방언 공통으로 둔다 (이슈 #23).
 */
final class SqlPromptBuilder {

    /** %1$s: 방언 표시명, %2$s: 방언 블록. */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a %1$s SQL generator. Generate a single, performant %1$s query.

            SCHEMA RULES:
            - Use ONLY the tables in "AVAILABLE TABLES". In FROM/JOIN, copy the quoted table name exactly as listed.
            - Use ONLY column names that appear in the CREATE TABLE statement for each table.
            - Quote a column name only if it is a reserved word or cannot be written unquoted (spaces, hyphens, other special characters, or mixed case in PostgreSQL), using the dialect quote style below.
            - The DDL section only describes columns and types. Its table names are unquoted and its syntax \
            is not the target dialect; do not copy it.

            VALUE RULES:
            - Use a literal code value (status, type, flag) ONLY if it appears in the column comment or table description; \
            then use it exactly as written.
            - If the needed value is not documented, do NOT guess. Write the placeholder '<CONFIRM_VALUE>' instead,
              and put a line comment ABOVE the statement: -- CONFIRM: <column> value for <meaning> is not in the definition.
            - All comments must come before the SQL statement. The response must end with the semicolon.

            %2$s
            QUERY QUALITY RULES:
            - Prefer JOIN over subqueries; write flat queries the optimizer can handle efficiently.
            - Use inferred relationships (FK columns ending in _id) to determine JOIN keys.
            - Apply WHERE filters on indexed columns before aggregating.
            - For ranking: flat SELECT + JOIN + GROUP BY + ORDER BY + the dialect row limit (no nested aggregation).
            - Select only the columns needed. Use short table aliases.
            - End with a semicolon.

            INTERPRETATION RULES:
            - If the request can be read more than one way (which date column a period applies to, what to exclude, \
            which state counts), pick the most natural reading and state it in ONE line ABOVE the statement: \
            -- 해석: <chosen reading>. Omit it when the request is unambiguous.
            - Judge a state (cancelled, paid, completed, withdrawn, etc.) ONLY by comparing the status column \
            (*_status, *_state, *_stat) with a value: status_col = '<value>' or status_col <> '<value>'.
            - A nullable date column (e.g. cancelled_at) tells WHEN, not WHETHER. Never use it with IS NULL or \
            IS NOT NULL to decide a state; use it only for a period filter.
            - If the state value is not documented, write status_col = '<CONFIRM_VALUE>' (or <>) with the -- CONFIRM \
            comment from the VALUE RULES. Only when the table has no status column, use another column and say so \
            in the -- 해석 line.
            - If a table has a soft-delete column (e.g. del_yn, is_deleted, deleted_flag), exclude deleted rows by default.

            LIST QUERY DEFAULTS (the request asks for rows, not totals):
            - Select representative columns (keys, name, status, main date, amount), never SELECT *.
            - ORDER BY the period or main date column DESC, and limit rows with the dialect row limit (N = 100 unless \
            the request gives a number).
            - Period filter: date_col >= start AND date_col < end (no BETWEEN on date/time columns).

            SELF-CHECK before output: deleted rows excluded? every state compared on the status column (or CONFIRM), \
            no state decided by a date IS NULL? period on the intended date column? list query has ORDER BY and row limit? no FORBIDDEN token of this dialect?

            OUTPUT: SQL query only, optionally preceded by -- 해석 and -- CONFIRM comments. \
            No explanation, no markdown, no code fences.
            """;

    private static final String MSSQL_BLOCK = """
            DIALECT: Microsoft SQL Server (T-SQL)
            - Quote identifiers with square brackets: [schema].[table], [column].
            - Current time: SYSDATETIME(). Date math: DATEADD(MONTH, -3, SYSDATETIME()). Date only: CAST(x AS date).
            - Row limit: SELECT TOP (N) ... or ORDER BY ... OFFSET 0 ROWS FETCH NEXT N ROWS ONLY.
            - Null fallback: ISNULL or COALESCE. String concat: CONCAT().
            - bit columns compare with 0/1 (no TRUE/FALSE literals). Compare nvarchar/nchar columns with N'...' literals.
            - FORBIDDEN in SQL Server: LIMIT, NOW(), INTERVAL, DATE_SUB, IFNULL, backtick (`) quotes.
            """;

    private static final String MYSQL_BLOCK = """
            DIALECT: MySQL
            - Quote identifiers with backticks: `schema`.`table`, `column`.
            - Current time: NOW(). Date math: DATE_SUB(NOW(), INTERVAL 3 MONTH). Date only: DATE(x).
            - Row limit: ORDER BY ... LIMIT N.
            - Null fallback: IFNULL or COALESCE. String concat: CONCAT().
            - TINYINT(1) flag columns compare with 0/1.
            - FORBIDDEN in MySQL: TOP, square bracket [ ] quotes, GETDATE(), :: casts.
            """;

    private static final String POSTGRESQL_BLOCK = """
            DIALECT: PostgreSQL
            - Quote identifiers with double quotes: "schema"."table", "column". Quoted names are case-sensitive.
            - Current time: NOW() or CURRENT_TIMESTAMP. Date math: NOW() - INTERVAL '3 months'. Date only: x::date.
            - Row limit: ORDER BY ... LIMIT N.
            - Null fallback: COALESCE. String concat: a || b or CONCAT().
            - boolean columns compare with TRUE/FALSE.
            - FORBIDDEN in PostgreSQL: TOP, square bracket [ ] quotes, backtick (`) quotes, DATE_SUB, IFNULL.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            AVAILABLE TABLES (use ONLY these):
            %s

            DDL + RELATIONSHIPS:
            %s

            Request: "%s"
            """;

    private SqlPromptBuilder() {
    }

    /**
     * 프롬프트 조립 입력.
     *
     * @param tables  프롬프트에 노출할 테이블 목록 (AVAILABLE TABLES)
     * @param ddl     DdlContextBuilder 가 만든 DDL 과 관계 요약
     * @param query   사용자 질의 원문
     * @param dialect SQL 생성 대상 방언. null 이면 저장/API 기본값과 같은 MYSQL
     */
    record Input(List<TableSchema> tables, String ddl, String query, SqlDialect dialect) {

        Input {
            if (dialect == null) {
                dialect = SqlDialect.MYSQL;
            }
        }
    }

    /** 조립 결과. LLM 에 보낼 시스템 프롬프트와 사용자 메시지. */
    record Result(String systemPrompt, String userMessage) {
    }

    static Result build(Input input) {
        return new Result(systemPrompt(input.dialect()), userMessage(input));
    }

    private static String systemPrompt(SqlDialect dialect) {
        return SYSTEM_PROMPT_TEMPLATE.formatted(displayName(dialect), dialectBlock(dialect));
    }

    private static String displayName(SqlDialect dialect) {
        return switch (dialect) {
            case MSSQL -> "SQL Server (T-SQL)";
            case MYSQL -> "MySQL";
            case POSTGRESQL -> "PostgreSQL";
        };
    }

    private static String dialectBlock(SqlDialect dialect) {
        return switch (dialect) {
            case MSSQL -> MSSQL_BLOCK;
            case MYSQL -> MYSQL_BLOCK;
            case POSTGRESQL -> POSTGRESQL_BLOCK;
        };
    }

    private static String userMessage(Input input) {
        SqlIdentifierQuoter quoter = DialectSqlIdentifierQuoter.of(input.dialect());
        return USER_MESSAGE_TEMPLATE.formatted(availableTables(input.tables(), quoter), input.ddl(), input.query());
    }

    private static String availableTables(List<TableSchema> tables, SqlIdentifierQuoter quoter) {
        return tables.stream()
                .map(s -> "  - " + quoter.quoteTableName(s.getTableName())
                        + (s.getTableDescription() != null && !s.getTableDescription().isBlank()
                            ? " (" + s.getTableDescription() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }
}
