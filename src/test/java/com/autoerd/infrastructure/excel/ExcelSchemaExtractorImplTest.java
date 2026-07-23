package com.autoerd.infrastructure.excel;

import com.autoerd.exception.AppException;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.TableSchema;
import com.autoerd.testsupport.ExcelFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelSchemaExtractorImplTest {

    private final ExcelSchemaExtractorImpl extractor = new ExcelSchemaExtractorImpl();

    @Test
    void koreanHeaderAliasesShouldParseTableAndColumns() throws Exception {
        byte[] xlsx = ExcelFixtures.standardKorean(
                new Object[]{"orders", "주문", 1, "id", "PK", "bigint", "20", "PRI", "NO", "auto_increment", ""},
                new Object[]{"orders", "주문", 2, "customer_id", "고객 FK", "bigint", "20", "MUL", "NO", "", ""}
        );

        List<TableSchema> schemas = extractor.extract(new ExtractedSchemaFile("orders.xlsx", xlsx.length, xlsx));

        assertThat(schemas).hasSize(1);
        TableSchema orders = schemas.get(0);
        assertThat(orders.getTableName()).isEqualTo("orders");
        assertThat(orders.getTableDescription()).isEqualTo("주문");
        assertThat(orders.getColumns()).hasSize(2);

        ColumnDef id = orders.getColumns().get(0);
        assertThat(id.getColumnName()).isEqualTo("id");
        assertThat(id.getOrdinal()).isEqualTo(1);
        assertThat(id.getKeyType()).isEqualTo(KeyType.PRI);
        assertThat(id.isNullable()).isFalse();
        assertThat(id.isAutoIncrement()).isTrue();

        ColumnDef customerId = orders.getColumns().get(1);
        assertThat(customerId.getKeyType()).isEqualTo(KeyType.MUL);
        assertThat(customerId.isAutoIncrement()).isFalse();
    }

    @Test
    void englishHeaderAliasesShouldParseEquivalently() throws Exception {
        byte[] xlsx = ExcelFixtures.standardEnglish(
                new Object[]{"customer", "고객", 1, "id", "PK", "bigint", "20", "PRI", "NO", "auto_increment", ""}
        );

        List<TableSchema> schemas = extractor.extract(new ExtractedSchemaFile("customer.xlsx", xlsx.length, xlsx));

        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).getTableName()).isEqualTo("customer");
        assertThat(schemas.get(0).getColumns().get(0).getKeyType()).isEqualTo(KeyType.PRI);
    }

    @Test
    void missingRequiredHeaderShouldThrowUnprocessable() {
        // 시트 감지(score>=3)는 통과하되, 필수 헤더(dataType)가 없는 경우
        String[] incompleteHeaders = {"테이블명", "컬럼명", "컬럼설명"};
        byte[] xlsx = ExcelFixtures.workbook(incompleteHeaders,
                new Object[]{"orders", "id", "설명"}
        );

        assertThatThrownBy(() -> extractor.extract(new ExtractedSchemaFile("bad.xlsx", xlsx.length, xlsx)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("필수 헤더 누락");
    }

    @Test
    void unrecognizableSheetShouldThrowUnprocessable() {
        // 매칭되는 헤더가 2개 이하 → 유효 시트로 인식되지 않음(score < 3)
        String[] junkHeaders = {"foo", "bar"};
        byte[] xlsx = ExcelFixtures.workbook(junkHeaders, new Object[]{"x", "y"});

        assertThatThrownBy(() -> extractor.extract(new ExtractedSchemaFile("junk.xlsx", xlsx.length, xlsx)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("유효한 테이블 정의서 시트를 찾을 수 없습니다");
    }

    @Test
    void defaultValueAndNullableShouldBeParsedPerRow() throws Exception {
        byte[] xlsx = ExcelFixtures.standardKorean(
                new Object[]{"product", "상품", 1, "id", "", "bigint", "20", "PRI", "NO", "auto_increment", ""},
                new Object[]{"product", "상품", 2, "stock", "재고", "int", "11", "", "YES", "", "0"}
        );

        List<TableSchema> schemas = extractor.extract(new ExtractedSchemaFile("product.xlsx", xlsx.length, xlsx));
        ColumnDef stock = schemas.get(0).getColumns().get(1);

        assertThat(stock.isNullable()).isTrue();
        assertThat(stock.getKeyType()).isEqualTo(KeyType.NONE);
        assertThat(stock.getDefaultValue()).isEqualTo("0");
    }

    @Test
    void blankDataTypeShouldFallBackToVarchar() throws Exception {
        String[] headers = {"테이블명", "컬럼명", "data type"};
        byte[] xlsx = ExcelFixtures.workbook(headers,
                new Object[]{"note", "content", ""}
        );

        List<TableSchema> schemas = extractor.extract(new ExtractedSchemaFile("note.xlsx", xlsx.length, xlsx));

        assertThat(schemas.get(0).getColumns().get(0).getDataType()).isEqualTo("varchar");
    }
}
