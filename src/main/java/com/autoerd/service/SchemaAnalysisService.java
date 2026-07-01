package com.autoerd.service;

import com.autoerd.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class SchemaAnalysisService {

    public List<TableRelation> inferRelations(List<TableSchema> schemas) {
        Set<String> tableNames = new HashSet<>();
        for (TableSchema s : schemas) tableNames.add(s.getTableName());

        List<TableRelation> relations = new ArrayList<>();

        for (TableSchema schema : schemas) {
            for (ColumnDef col : schema.getColumns()) {
                String colName = col.getColumnName();
                if (colName == null || colName.equals("id") || !colName.endsWith("_id")) continue;

                String refTable = colName.substring(0, colName.length() - 3);

                // 복합 suffix 처리: explore_dive_in_category_id → explore_dive_in_category
                if (!tableNames.contains(refTable)) {
                    refTable = findBestMatch(colName, tableNames);
                    if (refTable == null) continue;
                }

                RelationType relType = inferType(schema, refTable);

                relations.add(TableRelation.builder()
                        .fromTable(schema.getTableName())
                        .fromColumn(colName)
                        .toTable(refTable)
                        .toColumn("id")
                        .type(relType)
                        .build());

                log.debug("Relation: {}.{} -> {}.id [{}]",
                        schema.getTableName(), colName, refTable, relType);
            }
        }

        log.debug("Total relations inferred: {}", relations.size());
        return relations;
    }

    private String findBestMatch(String colName, Set<String> tableNames) {
        // 가장 긴 매칭 테이블명을 찾음 (greedy)
        String prefix = colName.substring(0, colName.length() - 3); // remove _id
        String best = null;
        int bestLen = 0;
        for (String t : tableNames) {
            if (prefix.endsWith(t) && t.length() > bestLen) {
                best = t;
                bestLen = t.length();
            }
        }
        return best;
    }

    private RelationType inferType(TableSchema from, String refTable) {
        if (from.getTableName().equals(refTable)) return RelationType.SELF_REFERENCE;
        // 중간 테이블 패턴: _nn_ 포함 또는 테이블명이 두 테이블명의 조합
        if (from.getTableName().contains("_nn_")) return RelationType.MANY_TO_MANY;
        return RelationType.ONE_TO_MANY;
    }
}
