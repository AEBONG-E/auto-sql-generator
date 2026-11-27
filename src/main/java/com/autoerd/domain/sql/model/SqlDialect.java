package com.autoerd.domain.sql.model;

/**
 * 프로젝트별 SQL 생성 대상 DB 방언 (이슈 #18).
 * metadata_project.sql_dialect 에 이름 그대로 저장한다.
 */
public enum SqlDialect {
    MSSQL,
    MYSQL,
    POSTGRESQL
}
