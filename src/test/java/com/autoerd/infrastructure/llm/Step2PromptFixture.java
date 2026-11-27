package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlDialect;
import com.autoerd.domain.sql.model.SqlGenerationRequest;
import com.autoerd.domain.sql.model.SqlMetadataSnapshot;
import com.autoerd.model.ColumnDef;
import com.autoerd.model.KeyType;
import com.autoerd.model.RelationType;
import com.autoerd.model.TableRelation;
import com.autoerd.model.TableSchema;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Step2 프롬프트 golden 비교용 고정 입력과 기대값 로더.
 * 기대값 파일은 SqlPromptBuilder 분리 전 코드(origin/feature/user 5e835d1)의 출력으로 처음 만들었고,
 * 이슈 #18 3단계에서 MYSQL 방언일 때의 출력(방언 블록, 값 규칙, 인용된 테이블명)으로 갱신했다.
 */
final class Step2PromptFixture {

    static final String SYSTEM_GOLDEN = "/prompt-golden/step2-system.txt";
    static final String USER_GOLDEN = "/prompt-golden/step2-user.txt";

    // 큰따옴표, 퍼센트 기호, 한글을 넣어 format 처리와 인코딩이 그대로인지 함께 고정한다.
    static final String QUERY = "활성 회원의 \"최근\" 3개월 주문을 100% 집계해줘";

    private Step2PromptFixture() {
    }

    static List<TableSchema> tables() {
        TableSchema user = TableSchema.builder()
                .tableName("member.user")
                .tableDescription("회원")
                .columns(List.of(
                        ColumnDef.builder().ordinal(1).columnName("user_id").columnType("bigint")
                                .keyType(KeyType.PRI).nullable(false).autoIncrement(true).build(),
                        ColumnDef.builder().ordinal(2).columnName("user_status").dataType("nvarchar")
                                .keyType(KeyType.NONE).nullable(true).columnComment("회원상태 ACTIVATED/WITHDRAWN").build()))
                .build();
        TableSchema order = TableSchema.builder()
                .tableName("order.sales_order")
                .tableDescription(" ")
                .columns(List.of(
                        ColumnDef.builder().ordinal(1).columnName("sales_order_id").columnType("bigint")
                                .keyType(KeyType.PRI).nullable(false).build(),
                        ColumnDef.builder().ordinal(2).columnName("user_id").columnType("bigint")
                                .keyType(KeyType.MUL).nullable(false).build(),
                        ColumnDef.builder().ordinal(3).columnName("delete_yn").columnType("nchar(1)")
                                .keyType(KeyType.NONE).nullable(false).defaultValue("N").build()))
                .build();
        TableSchema noDesc = TableSchema.builder()
                .tableName("tb_code")
                .columns(List.of(
                        ColumnDef.builder().ordinal(1).columnName("code").columnType("varchar(20)")
                                .keyType(KeyType.PRI).nullable(false).build()))
                .build();
        return List.of(user, order, noDesc);
    }

    static List<TableRelation> relations() {
        return List.of(TableRelation.builder()
                .fromTable("order.sales_order").fromColumn("user_id")
                .toTable("member.user").toColumn("user_id")
                .type(RelationType.ONE_TO_MANY).build());
    }

    static SqlGenerationRequest request() {
        return new SqlGenerationRequest(7L, QUERY, new SqlMetadataSnapshot(tables(), relations(), SqlDialect.MYSQL));
    }

    static String golden(String path) {
        try (InputStream in = Step2PromptFixture.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("golden 파일이 없습니다: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
