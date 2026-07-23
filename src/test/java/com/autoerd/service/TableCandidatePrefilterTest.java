package com.autoerd.service;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableCandidatePrefilterTest {

    private TableCandidatePrefilter newPrefilter(boolean enabled, int minTables, int maxCandidates) {
        TableCandidatePrefilter p = new TableCandidatePrefilter();
        ReflectionTestUtils.setField(p, "enabled", enabled);
        ReflectionTestUtils.setField(p, "minTables", minTables);
        ReflectionTestUtils.setField(p, "maxCandidates", maxCandidates);
        return p;
    }

    private TableSchema table(String name, String desc, String... columns) {
        List<ColumnDef> cols = new ArrayList<>();
        int i = 0;
        for (String c : columns) {
            cols.add(ColumnDef.builder()
                    .ordinal(i++)
                    .columnName(c)
                    .keyType(KeyType.NONE)
                    .nullable(true)
                    .build());
        }
        return TableSchema.builder().tableName(name).tableDescription(desc).columns(cols).build();
    }

    /** 90테이블 규모에서 질의 키워드에 맞는 소수 후보로 축소되어야 한다. */
    @Test
    void reducesLargeSchemaToKeywordMatchedCandidates() {
        List<TableSchema> tables = new ArrayList<>();
        tables.add(table("user", "회원", "id", "name"));
        tables.add(table("user_detail", "회원 상세", "user_id", "address"));
        tables.add(table("user_agreement", "회원 약관 동의", "user_id", "agreed"));
        for (int i = 0; i < 87; i++) {
            tables.add(table("misc_" + i, "기타 " + i, "id", "value"));
        }
        assertThat(tables).hasSize(90);

        TableCandidatePrefilter p = newPrefilter(true, 30, 30);
        List<TableSchema> result = p.prefilter("user 관련 정보 조회", tables, List.of());

        List<String> names = result.stream().map(TableSchema::getTableName).toList();
        assertThat(result.size()).isLessThan(90);
        assertThat(names).contains("user", "user_detail", "user_agreement");
        assertThat(names).doesNotContain("misc_0", "misc_42");
    }

    /** 소형 스키마(min-tables 이하)는 그대로 전체를 반환해야 한다(회귀 방지). */
    @Test
    void smallSchemaReturnsAllUnchanged() {
        List<TableSchema> tables = List.of(
                table("orders", "주문", "id"),
                table("customers", "고객", "id"));

        TableCandidatePrefilter p = newPrefilter(true, 30, 30);
        List<TableSchema> result = p.prefilter("무관한 질의 xyz", tables, List.of());

        assertThat(result).isSameAs(tables);
    }

    /** 매칭 0건이면 전체 폴백해야 한다(정확도 보존). */
    @Test
    void zeroMatchFallsBackToAll() {
        List<TableSchema> tables = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tables.add(table("table_" + i, "설명 " + i, "col_a", "col_b"));
        }

        TableCandidatePrefilter p = newPrefilter(true, 30, 30);
        List<TableSchema> result = p.prefilter("완전히무관한키워드zzz", tables, List.of());

        assertThat(result).hasSize(40); // 폴백: 전체 유지
    }

    /** 관계가 있으면 1-hop 이웃(브리지 테이블)을 후보에 포함해야 한다. */
    @Test
    void includesOneHopRelationNeighbor() {
        List<TableSchema> tables = new ArrayList<>();
        tables.add(table("orders", "주문", "id", "customer_id"));
        tables.add(table("order_items", "주문 항목", "id", "order_id", "product_id"));
        for (int i = 0; i < 40; i++) {
            tables.add(table("noise_" + i, "노이즈 " + i, "id"));
        }
        // orders <- order_items (order_id), order_items -> product (product_id 미존재 테이블은 무시됨)
        List<TableRelation> relations = List.of(
                TableRelation.builder()
                        .fromTable("order_items").fromColumn("order_id")
                        .toTable("orders").toColumn("id")
                        .type(RelationType.ONE_TO_MANY).build());

        TableCandidatePrefilter p = newPrefilter(true, 30, 30);
        // 질의는 orders만 직접 언급 → order_items는 관계로만 포함되어야 함
        List<TableSchema> result = p.prefilter("orders 조회", tables, relations);
        List<String> names = result.stream().map(TableSchema::getTableName).toList();

        assertThat(names).contains("orders", "order_items");
    }

    /** enabled=false이면 전체를 그대로 반환한다. */
    @Test
    void disabledReturnsAll() {
        List<TableSchema> tables = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tables.add(table("user_" + i, "회원 " + i, "id"));
        }
        TableCandidatePrefilter p = newPrefilter(false, 30, 30);
        List<TableSchema> result = p.prefilter("user", tables, List.of());
        assertThat(result).isSameAs(tables);
    }
}
