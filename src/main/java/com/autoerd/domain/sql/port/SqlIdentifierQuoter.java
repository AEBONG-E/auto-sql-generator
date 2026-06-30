package com.autoerd.domain.sql.port;

public interface SqlIdentifierQuoter {

    String quoteIdentifier(String rawIdentifier);

    String quoteQualifiedName(String schemaName, String objectName);
}
