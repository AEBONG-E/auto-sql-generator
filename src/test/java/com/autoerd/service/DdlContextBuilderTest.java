package com.autoerd.service;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DdlContextBuilderTest {

    private final DdlContextBuilder builder = new DdlContextBuilder();

    @Test
    void buildShouldIncludeKeyAndRelationHints() {
        var orderSchema = TableSchema.builder()
                .tableName("orders")
                .tableDescription("주문")
                .columns(List.of(
                        ColumnDef.builder()
                                .columnName("id")
                                .columnType("bigint")
                                .keyType(KeyType.PRI)
                                .nullable(false)
                                .autoIncrement(true)
                                .build(),
                        ColumnDef.builder()
                                .columnName("customer_id")
                                .columnType("bigint")
                                .keyType(KeyType.MUL)
                                .nullable(false)
                                .build(),
                        ColumnDef.builder()
                                .columnName("total_amount")
                                .columnType("decimal(12,2)")
                                .defaultValue("0.00")
                                .nullable(false)
                                .build()
                ))
                .build();

        var customerSchema = TableSchema.builder()
                .tableName("customer")
                .columns(List.of(
                        ColumnDef.builder()
                                .columnName("id")
                                .columnType("bigint")
                                .keyType(KeyType.PRI)
                                .nullable(false)
                                .build()
                ))
                .build();

        var relation = TableRelation.builder()
                .fromTable("orders")
                .fromColumn("customer_id")
                .toTable("customer")
                .toColumn("id")
                .type(RelationType.ONE_TO_MANY)
                .build();

        String ddl = builder.build(List.of(orderSchema, customerSchema), List.of(relation));

        assertThat(ddl).contains("-- PRIMARY KEY: id");
        assertThat(ddl).contains("-- INDEXED COLUMNS: customer_id");
        assertThat(ddl).contains("DEFAULT '0.00'");
        assertThat(ddl).contains("-- Inferred Relationships");
        assertThat(ddl).contains("-- orders.customer_id -> customer.id [ONE_TO_MANY]");
    }
}
