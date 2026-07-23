package com.autoerd.domain.metadata.policy;

import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultRelationInferencePolicyTest {

    private final DefaultRelationInferencePolicy policy = new DefaultRelationInferencePolicy();

    private TableSchema table(String name, String... columnNames) {
        List<ColumnDef> columns = List.of(columnNames).stream()
                .map(c -> ColumnDef.builder().columnName(c).keyType(KeyType.NONE).nullable(true).build())
                .toList();
        return TableSchema.builder().tableName(name).columns(columns).build();
    }

    @Test
    void columnEndingWithIdShouldInferOneToManyToMatchingTable() {
        List<TableSchema> schemas = List.of(
                table("orders", "id", "customer_id"),
                table("customer", "id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).hasSize(1);
        TableRelation rel = relations.get(0);
        assertThat(rel.getFromTable()).isEqualTo("orders");
        assertThat(rel.getFromColumn()).isEqualTo("customer_id");
        assertThat(rel.getToTable()).isEqualTo("customer");
        assertThat(rel.getToColumn()).isEqualTo("id");
        assertThat(rel.getType()).isEqualTo(RelationType.ONE_TO_MANY);
    }

    @Test
    void columnNamedIdShouldBeExcludedFromInference() {
        List<TableSchema> schemas = List.of(table("customer", "id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).isEmpty();
    }

    @Test
    void selfReferencingColumnShouldInferSelfReferenceType() {
        // 휴리스틱은 컬럼명이 "<자기테이블명>_id"일 때만 자기참조로 해석한다("parent_id"는 매칭 테이블이 없어 무시됨)
        List<TableSchema> schemas = List.of(table("category", "id", "category_id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getType()).isEqualTo(RelationType.SELF_REFERENCE);
        assertThat(relations.get(0).getToTable()).isEqualTo("category");
    }

    @Test
    void manyToManyBridgeTableNamingShouldInferManyToMany() {
        // "_nn_" 네이밍 컨벤션을 가진 브리지 테이블
        List<TableSchema> schemas = List.of(
                table("order_nn_product", "id", "order_id", "product_id"),
                table("order", "id"),
                table("product", "id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).allSatisfy(r -> assertThat(r.getType()).isEqualTo(RelationType.MANY_TO_MANY));
    }

    @Test
    void unmatchedForeignKeyShouldBeSkipped() {
        // "product_id" 참조 테이블(product)이 스키마 목록에 없음 → 관계 미생성
        List<TableSchema> schemas = List.of(table("orders", "id", "product_id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).isEmpty();
    }

    @Test
    void bestMatchShouldResolveViaLongestTableNameSuffix() {
        // "store_branch_id" 컬럼: 직접 일치하는 "store_branch" 테이블은 없으나
        // findBestMatch가 prefix("store_branch")의 접미사로 가장 긴 테이블명("branch")을 선택해야 함
        List<TableSchema> schemas = List.of(
                table("shipment", "id", "store_branch_id"),
                table("branch", "id"));

        List<TableRelation> relations = policy.inferRelations(schemas);

        assertThat(relations).hasSize(1);
        assertThat(relations.get(0).getToTable()).isEqualTo("branch");
    }
}
