package com.autoerd.domain.sql.port;

public interface SqlIdentifierQuoter {

    String quoteIdentifier(String rawIdentifier);

    String quoteQualifiedName(String schemaName, String objectName);

    /**
     * 정의서 테이블명 문자열("schema.table" 또는 "table")을 인용한다.
     * 첫 '.' 기준으로 schema 와 object 를 나눈다 (이슈 #18).
     */
    default String quoteTableName(String rawTableName) {
        if (rawTableName == null) {
            throw new IllegalArgumentException("테이블명이 비어 있습니다");
        }
        int dot = rawTableName.indexOf('.');
        if (dot < 0) {
            return quoteIdentifier(rawTableName);
        }
        return quoteQualifiedName(rawTableName.substring(0, dot), rawTableName.substring(dot + 1));
    }
}
