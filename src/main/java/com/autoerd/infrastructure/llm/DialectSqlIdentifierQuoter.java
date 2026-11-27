package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.port.SqlIdentifierQuoter;

import java.util.EnumMap;
import java.util.Map;

/**
 * 방언별 식별자 인용 (이슈 #18).
 * MSSQL [name], MYSQL `name`, POSTGRESQL "name". 이름 안의 닫는 기호는 이중화한다.
 * 상태가 없으므로 방언당 인스턴스 하나를 재사용한다.
 */
public final class DialectSqlIdentifierQuoter implements SqlIdentifierQuoter {

    private static final Map<SqlDialect, DialectSqlIdentifierQuoter> INSTANCES = new EnumMap<>(SqlDialect.class);

    static {
        for (SqlDialect dialect : SqlDialect.values()) {
            INSTANCES.put(dialect, new DialectSqlIdentifierQuoter(dialect));
        }
    }

    private final String open;
    private final String close;

    private DialectSqlIdentifierQuoter(SqlDialect dialect) {
        switch (dialect) {
            case MSSQL -> {
                this.open = "[";
                this.close = "]";
            }
            case MYSQL -> {
                this.open = "`";
                this.close = "`";
            }
            case POSTGRESQL -> {
                this.open = "\"";
                this.close = "\"";
            }
            default -> throw new IllegalArgumentException("지원하지 않는 방언입니다: " + dialect);
        }
    }

    public static DialectSqlIdentifierQuoter of(SqlDialect dialect) {
        if (dialect == null) {
            throw new IllegalArgumentException("방언이 비어 있습니다");
        }
        return INSTANCES.get(dialect);
    }

    @Override
    public String quoteIdentifier(String rawIdentifier) {
        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new IllegalArgumentException("식별자가 비어 있습니다");
        }
        return open + rawIdentifier.replace(close, close + close) + close;
    }

    @Override
    public String quoteQualifiedName(String schemaName, String objectName) {
        if (schemaName == null || schemaName.isBlank()) {
            return quoteIdentifier(objectName);
        }
        return quoteIdentifier(schemaName) + "." + quoteIdentifier(objectName);
    }
}
