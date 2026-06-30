package com.autoerd.infrastructure.assembler;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MetadataDomainAssembler {

    public TableSchema toTableSchema(MetadataTableEntity tableEntity, List<MetadataColumnEntity> columnEntities) {
        List<ColumnDef> columns = columnEntities.stream()
                .map(this::toColumnDef)
                .toList();
        return TableSchema.builder()
                .tableName(tableEntity.getTableName())
                .tableDescription(tableEntity.getTableDescription())
                .columns(columns)
                .build();
    }

    public ColumnDef toColumnDef(MetadataColumnEntity col) {
        return ColumnDef.builder()
                .ordinal(col.getOrdinalPosition())
                .columnName(col.getColumnName())
                .columnComment(col.getColumnComment())
                .dataType(col.getDataType())
                .columnType(col.getColumnType())
                .keyType(KeyType.from(col.getKeyType()))
                .nullable("Y".equalsIgnoreCase(col.getNullableYn()))
                .autoIncrement("Y".equalsIgnoreCase(col.getAutoIncrementYn()))
                .defaultValue(col.getDefaultValue())
                .build();
    }

    public TableRelation toTableRelation(MetadataRelationEntity rel) {
        return TableRelation.builder()
                .fromTable(rel.getFromTable().getTableName())
                .fromColumn(rel.getFromColumn().getColumnName())
                .toTable(rel.getToTable().getTableName())
                .toColumn(rel.getToColumn().getColumnName())
                .type(RelationType.valueOf(rel.getRelationType()))
                .build();
    }
}
