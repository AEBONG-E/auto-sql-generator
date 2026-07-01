package com.autoerd.service;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DdlContextBuilder {

    public String build(List<TableSchema> schemas) {
        return build(schemas, List.of());
    }

    public String build(List<TableSchema> schemas, List<TableRelation> relations) {
        if (schemas == null || schemas.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder();
        for (TableSchema table : schemas) {
            sb.append("-- ").append(table.getTableName());
            if (table.getTableDescription() != null && !table.getTableDescription().isBlank()) {
                sb.append(" (").append(table.getTableDescription()).append(")");
            }
            sb.append("\nCREATE TABLE ").append(table.getTableName()).append(" (\n");

            List<ColumnDef> cols = table.getColumns();
            for (int i = 0; i < cols.size(); i++) {
                ColumnDef col = cols.get(i);
                sb.append("  ").append(col.getColumnName());
                if (col.getColumnType() != null && !col.getColumnType().isBlank()) {
                    sb.append(" ").append(col.getColumnType());
                } else if (col.getDataType() != null && !col.getDataType().isBlank()) {
                    sb.append(" ").append(col.getDataType());
                }
                if (!col.isNullable()) {
                    sb.append(" NOT NULL");
                }
                if (col.isAutoIncrement()) {
                    sb.append(" AUTO_INCREMENT");
                }
                if (col.getDefaultValue() != null && !col.getDefaultValue().isBlank()) {
                    sb.append(" DEFAULT '").append(col.getDefaultValue().replace("'", "\\'")).append("'");
                }
                if (col.getColumnComment() != null && !col.getColumnComment().isBlank()) {
                    sb.append(" COMMENT '").append(col.getColumnComment().replace("'", "\\'")).append("'");
                }
                if (i < cols.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            appendKeySummary(sb, cols);
            sb.append(");\n\n");
        }
        appendRelationSummary(sb, relations);
        return sb.toString();
    }

    private void appendKeySummary(StringBuilder sb, List<ColumnDef> cols) {
        var primaryKeys = cols.stream()
                .filter(col -> col.getKeyType() == KeyType.PRI)
                .map(ColumnDef::getColumnName)
                .toList();
        if (!primaryKeys.isEmpty()) {
            sb.append("-- PRIMARY KEY: ").append(String.join(", ", primaryKeys)).append("\n");
        }

        var uniqueKeys = cols.stream()
                .filter(col -> col.getKeyType() == KeyType.UNI)
                .map(ColumnDef::getColumnName)
                .toList();
        if (!uniqueKeys.isEmpty()) {
            sb.append("-- UNIQUE KEY: ").append(String.join(", ", uniqueKeys)).append("\n");
        }

        var indexedKeys = cols.stream()
                .filter(col -> col.getKeyType() == KeyType.MUL)
                .map(ColumnDef::getColumnName)
                .toList();
        if (!indexedKeys.isEmpty()) {
            sb.append("-- INDEXED COLUMNS: ").append(String.join(", ", indexedKeys)).append("\n");
        }
    }

    private void appendRelationSummary(StringBuilder sb, List<TableRelation> relations) {
        if (relations == null || relations.isEmpty()) {
            return;
        }

        sb.append("-- Inferred Relationships\n");
        for (TableRelation relation : relations) {
            sb.append("-- ")
                    .append(relation.getFromTable()).append(".").append(relation.getFromColumn())
                    .append(" -> ")
                    .append(relation.getToTable()).append(".").append(relation.getToColumn())
                    .append(" [").append(relation.getType().name()).append("]")
                    .append("\n");
        }
        sb.append("\n");
    }
}
