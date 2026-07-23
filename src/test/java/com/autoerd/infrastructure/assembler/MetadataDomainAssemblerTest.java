package com.autoerd.infrastructure.assembler;

import com.autoerd.infrastructure.persistence.jpa.entity.MetadataColumnEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataRelationEntity;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataTableEntity;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataDomainAssemblerTest {

    private final MetadataDomainAssembler assembler = new MetadataDomainAssembler();

    @Test
    void toColumnDefShouldMapYNFlagsAndKeyTypeCorrectly() {
        MetadataColumnEntity column = MetadataColumnEntity.builder()
                .ordinalPosition(2)
                .columnName("customer_id")
                .columnComment("고객 FK")
                .dataType("bigint")
                .columnType("bigint(20)")
                .keyType("MUL")
                .nullableYn("N")
                .autoIncrementYn("y")
                .defaultValue("0")
                .build();

        ColumnDef def = assembler.toColumnDef(column);

        assertThat(def.getOrdinal()).isEqualTo(2);
        assertThat(def.getColumnName()).isEqualTo("customer_id");
        assertThat(def.getKeyType()).isEqualTo(KeyType.MUL);
        assertThat(def.isNullable()).isFalse();
        assertThat(def.isAutoIncrement()).isTrue(); // 대소문자 무관하게 "Y" 취급
        assertThat(def.getDefaultValue()).isEqualTo("0");
    }

    @Test
    void toTableSchemaShouldAssembleTableAndOrderedColumns() {
        MetadataTableEntity table = MetadataTableEntity.builder()
                .tableName("orders")
                .tableDescription("주문")
                .build();

        MetadataColumnEntity col1 = MetadataColumnEntity.builder()
                .ordinalPosition(1).columnName("id").dataType("bigint")
                .keyType("PRI").nullableYn("N").autoIncrementYn("Y").build();
        MetadataColumnEntity col2 = MetadataColumnEntity.builder()
                .ordinalPosition(2).columnName("customer_id").dataType("bigint")
                .keyType("MUL").nullableYn("N").autoIncrementYn("N").build();

        TableSchema schema = assembler.toTableSchema(table, List.of(col1, col2));

        assertThat(schema.getTableName()).isEqualTo("orders");
        assertThat(schema.getTableDescription()).isEqualTo("주문");
        assertThat(schema.getColumns()).extracting(ColumnDef::getColumnName)
                .containsExactly("id", "customer_id");
    }

    @Test
    void toTableRelationShouldMapTableAndColumnNamesAndType() {
        MetadataTableEntity fromTable = MetadataTableEntity.builder().tableName("orders").build();
        MetadataTableEntity toTable = MetadataTableEntity.builder().tableName("customer").build();
        MetadataColumnEntity fromColumn = MetadataColumnEntity.builder().columnName("customer_id").dataType("bigint").build();
        MetadataColumnEntity toColumn = MetadataColumnEntity.builder().columnName("id").dataType("bigint").build();

        MetadataRelationEntity relation = MetadataRelationEntity.builder()
                .fromTable(fromTable)
                .fromColumn(fromColumn)
                .toTable(toTable)
                .toColumn(toColumn)
                .relationType(RelationType.ONE_TO_MANY.name())
                .build();

        TableRelation result = assembler.toTableRelation(relation);

        assertThat(result.getFromTable()).isEqualTo("orders");
        assertThat(result.getFromColumn()).isEqualTo("customer_id");
        assertThat(result.getToTable()).isEqualTo("customer");
        assertThat(result.getToColumn()).isEqualTo("id");
        assertThat(result.getType()).isEqualTo(RelationType.ONE_TO_MANY);
    }
}
