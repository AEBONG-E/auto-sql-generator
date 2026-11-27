package com.autoerd.domain.sql.model;

import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;

import java.util.List;

/**
 * Step2 SQL 생성에 쓰는 프로젝트 메타데이터.
 *
 * @param dialect 프로젝트의 SQL 생성 대상 방언 (이슈 #18). null 이면 저장/API 기본값과 같은 MYSQL.
 */
public record SqlMetadataSnapshot(
        List<TableSchema> tables,
        List<TableRelation> relations,
        SqlDialect dialect
) {

    public SqlMetadataSnapshot {
        if (dialect == null) {
            dialect = SqlDialect.MYSQL;
        }
    }

    /** 방언을 지정하지 않으면 기본값 MYSQL 을 쓴다. */
    public SqlMetadataSnapshot(List<TableSchema> tables, List<TableRelation> relations) {
        this(tables, relations, SqlDialect.MYSQL);
    }
}
