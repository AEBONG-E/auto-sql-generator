package com.autoerd.dto;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ErdResponse {
    private final Stats stats;
    private final List<TableInfo> tables;
    private final List<RelationInfo> relations;
    private final List<FileInfo> files;

    @Getter
    @Builder
    public static class Stats {
        private final int tableCount;
        private final int columnCount;
        private final int relationCount;
    }

    @Getter
    @Builder
    public static class FileInfo {
        private final String filename;
        private final int tableCount;
    }

    @Getter
    @Builder
    public static class TableInfo {
        private final String tableName;
        private final String tableDescription;
        private final int columnCount;
        private final List<ColumnInfo> columns;
    }

    @Getter
    @Builder
    public static class ColumnInfo {
        private final int ordinal;
        private final String columnName;
        private final String columnComment;
        private final String dataType;
        private final String columnType;
        private final String keyType;
        private final boolean nullable;
        private final boolean autoIncrement;
        private final String defaultValue;
    }

    @Getter
    @Builder
    public static class RelationInfo {
        private final String fromTable;
        private final String fromColumn;
        private final String toTable;
        private final String relationType;
    }

    /**
     * 3인자 버전 (fileSummary 포함)
     */
    public static ErdResponse from(List<TableSchema> schemas, List<TableRelation> relations, Map<String, Integer> fileSummary) {
        List<TableInfo> tableInfos = schemas.stream()
                .map(s -> TableInfo.builder()
                        .tableName(s.getTableName())
                        .tableDescription(s.getTableDescription())
                        .columnCount(s.getColumns().size())
                        .columns(s.getColumns().stream()
                                .map(ErdResponse::toColumnInfo)
                                .toList())
                        .build())
                .toList();

        List<RelationInfo> relationInfos = relations.stream()
                .map(r -> RelationInfo.builder()
                        .fromTable(r.getFromTable())
                        .fromColumn(r.getFromColumn())
                        .toTable(r.getToTable())
                        .relationType(r.getType().name())
                        .build())
                .toList();

        int totalColumns = schemas.stream().mapToInt(s -> s.getColumns().size()).sum();

        List<FileInfo> fileInfos = (fileSummary != null)
                ? fileSummary.entrySet().stream()
                    .map(e -> FileInfo.builder().filename(e.getKey()).tableCount(e.getValue()).build())
                    .toList()
                : List.of();

        return ErdResponse.builder()
                .stats(Stats.builder()
                        .tableCount(schemas.size())
                        .columnCount(totalColumns)
                        .relationCount(relations.size())
                        .build())
                .tables(tableInfos)
                .relations(relationInfos)
                .files(fileInfos)
                .build();
    }

    /**
     * 하위 호환: 2인자 버전 (fileSummary 없음 → 빈 리스트)
     */
    public static ErdResponse from(List<TableSchema> schemas, List<TableRelation> relations) {
        return from(schemas, relations, Map.of());
    }

    private static ColumnInfo toColumnInfo(ColumnDef col) {
        return ColumnInfo.builder()
                .ordinal(col.getOrdinal())
                .columnName(col.getColumnName())
                .columnComment(col.getColumnComment())
                .dataType(col.getDataType())
                .columnType(col.getColumnType())
                .keyType(col.getKeyType().name())
                .nullable(col.isNullable())
                .autoIncrement(col.isAutoIncrement())
                .defaultValue(col.getDefaultValue())
                .build();
    }
}
