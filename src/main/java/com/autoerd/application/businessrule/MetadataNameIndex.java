package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleTargetStatus;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.TableSchema;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 현재 활성 메타데이터의 테이블/컬럼 이름 색인. 비교는 대소문자를 무시한다
 * (Step1 응답 매핑과 같은 기준). 조회 결과는 메타데이터에 저장된 원래 표기를 돌려준다.
 */
final class MetadataNameIndex {

    /** 소문자 테이블명 -> (원래 테이블명, 소문자 컬럼명 -> 원래 컬럼명) */
    private final Map<String, TableEntry> tables;

    private record TableEntry(String name, Map<String, String> columns) {
    }

    private MetadataNameIndex(Map<String, TableEntry> tables) {
        this.tables = tables;
    }

    static MetadataNameIndex of(SqlMetadataSnapshot snapshot) {
        Map<String, TableEntry> tables = new HashMap<>();
        if (snapshot != null && snapshot.tables() != null) {
            for (TableSchema table : snapshot.tables()) {
                if (table.getTableName() == null) {
                    continue;
                }
                Map<String, String> columns = new HashMap<>();
                if (table.getColumns() != null) {
                    for (ColumnDef column : table.getColumns()) {
                        if (column.getColumnName() != null) {
                            columns.putIfAbsent(key(column.getColumnName()), column.getColumnName());
                        }
                    }
                }
                tables.putIfAbsent(key(table.getTableName()), new TableEntry(table.getTableName(), columns));
            }
        }
        return new MetadataNameIndex(tables);
    }

    Optional<String> table(String tableName) {
        if (tableName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tables.get(key(tableName))).map(TableEntry::name);
    }

    Optional<String> column(String tableName, String columnName) {
        if (tableName == null || columnName == null) {
            return Optional.empty();
        }
        TableEntry entry = tables.get(key(tableName));
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.columns().get(key(columnName)));
    }

    BusinessRuleTargetStatus statusOf(String tableName, String columnName, String relatedTableName) {
        if (table(tableName).isEmpty()) {
            return BusinessRuleTargetStatus.TABLE_MISSING;
        }
        if (columnName != null && column(tableName, columnName).isEmpty()) {
            return BusinessRuleTargetStatus.COLUMN_MISSING;
        }
        if (relatedTableName != null && table(relatedTableName).isEmpty()) {
            return BusinessRuleTargetStatus.RELATED_TABLE_MISSING;
        }
        return BusinessRuleTargetStatus.OK;
    }

    static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
