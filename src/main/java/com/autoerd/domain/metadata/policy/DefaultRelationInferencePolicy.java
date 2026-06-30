package com.autoerd.domain.metadata.policy;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class DefaultRelationInferencePolicy implements RelationInferencePolicy {

    @Override
    public List<TableRelation> inferRelations(List<TableSchema> schemas) {
        Set<String> tableNames = new HashSet<>();
        for (TableSchema s : schemas) {
            tableNames.add(s.getTableName());
        }

        List<TableRelation> relations = new ArrayList<>();

        for (TableSchema schema : schemas) {
            for (ColumnDef col : schema.getColumns()) {
                String colName = col.getColumnName();
                if (colName == null || colName.equals("id") || !colName.endsWith("_id")) continue;

                String refTable = colName.substring(0, colName.length() - 3);

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

                log.debug("Relation: {}.{} -> {}.id [{}]", schema.getTableName(), colName, refTable, relType);
            }
        }

        log.debug("Total relations inferred: {}", relations.size());
        return relations;
    }

    private String findBestMatch(String colName, Set<String> tableNames) {
        String prefix = colName.substring(0, colName.length() - 3);
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
        if (from.getTableName().contains("_nn_")) return RelationType.MANY_TO_MANY;
        return RelationType.ONE_TO_MANY;
    }
}
