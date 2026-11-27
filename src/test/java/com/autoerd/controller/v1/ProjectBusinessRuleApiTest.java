package com.autoerd.controller.v1;

import com.autoerd.application.project.ProjectService;
import com.autoerd.infrastructure.persistence.jpa.entity.MetadataSourceFileEntity;
import com.autoerd.infrastructure.persistence.jpa.repository.MetadataSourceFileRepository;
import com.autoerd.infrastructure.persistence.jpa.repository.ProjectBusinessRuleRepository;
import com.autoerd.testsupport.ExcelFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 이슈 #24 W1: 프로젝트 업무 규칙 저장과 CRUD API.
 * 가상 정의서(shop_member, shop_order, shop_payment)를 파일 3개로 나눠 올려, 파일 삭제와 재업로드로
 * 대상이 사라지는 상황을 만든다. 클래스 단위 @Transactional 로 각 테스트를 롤백해 격리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProjectBusinessRuleApiTest {

    private static final String BASE = "/api/v1/projects/{projectId}/business-rules";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProjectService projectService;
    @Autowired
    private MetadataSourceFileRepository sourceFileRepository;
    @Autowired
    private ProjectBusinessRuleRepository ruleRepository;

    private Long projectId;

    @BeforeEach
    void setUp() throws Exception {
        projectId = projectService.createProject("br-" + System.nanoTime(), "rules", null).getId();
        upload("member.xlsx", ExcelFixtures.standardKorean(
                new Object[]{"shop_member", "회원", 1, "member_id", "", "bigint", "20", "PRI", "NO", "", ""},
                new Object[]{"shop_member", "회원", 2, "joined_at", "가입일시", "datetime", "", "", "NO", "", ""}));
        upload("order.xlsx", ExcelFixtures.standardKorean(
                new Object[]{"shop_order", "주문", 1, "order_id", "", "bigint", "20", "PRI", "NO", "", ""},
                new Object[]{"shop_order", "주문", 2, "member_id", "", "bigint", "20", "MUL", "NO", "", ""},
                new Object[]{"shop_order", "주문", 3, "order_status", "주문 상태", "varchar", "2", "", "NO", "", ""},
                new Object[]{"shop_order", "주문", 4, "ordered_at", "주문일시", "datetime", "", "", "NO", "", ""},
                new Object[]{"shop_order", "주문", 5, "deleted_yn", "삭제 여부", "char", "1", "", "NO", "", "N"}));
        upload("payment.xlsx", ExcelFixtures.standardKorean(
                new Object[]{"shop_payment", "결제", 1, "payment_id", "", "bigint", "20", "PRI", "NO", "", ""},
                new Object[]{"shop_payment", "결제", 2, "order_id", "", "bigint", "20", "MUL", "NO", "", ""},
                new Object[]{"shop_payment", "결제", 3, "paid_at", "결제일시", "datetime", "", "", "NO", "", ""}));
    }

    private void upload(String filename, byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", projectId)
                        .file(new MockMultipartFile("files", filename, "application/octet-stream", content)))
                .andExpect(status().isOk());
    }

    private Long fileId(String filename) {
        return sourceFileRepository.findAllByProjectIdAndIsDeletedFalseOrderByUploadedAtDesc(projectId).stream()
                .filter(f -> f.getOriginalFilename().equals(filename))
                .map(MetadataSourceFileEntity::getId)
                .findFirst()
                .orElseThrow();
    }

    private ResultActions postRule(String json) throws Exception {
        return mockMvc.perform(post(BASE, projectId).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Long createRule(String json) throws Exception {
        String body = postRule(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static final String TERM_RULE = """
            {"ruleType":"term","term":"취소 주문","termAliases":" 취소된 주문 , ,주문 취소",
             "tableName":"SHOP_ORDER","columnName":"Order_Status",
             "conditionText":"order_status IN ('C1', 'C2')",
             "valueItems":[{"code":"C1","label":"고객 취소"},{"code":"C2","label":"관리자 취소"}],
             "relatedTableName":"shop_payment","autoInclude":true,
             "description":"환불 완료 건도 취소로 본다"}""";
    private static final String FILTER_RULE = """
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"deleted_yn = 'N'"}""";
    private static final String PERIOD_RULE = """
            {"ruleType":"PERIOD_COLUMN","tableName":"shop_order","columnName":"ordered_at"}""";
    private static final String JOIN_RULE = """
            {"ruleType":"JOIN_WITH","tableName":"shop_order","relatedTableName":"shop_payment",
             "conditionText":"shop_payment.order_id = shop_order.order_id","autoInclude":true}""";
    private static final String MEMBER_PERIOD_RULE = """
            {"ruleType":"PERIOD_COLUMN","tableName":"shop_member","columnName":"joined_at"}""";

    @Test
    @DisplayName("등록하면 201 과 정규화된 본문을 돌려주고, 이름은 메타데이터 표기로 저장하며 유형에 없는 필드는 버린다")
    void create_returns201WithNormalizedBody() throws Exception {
        postRule(TERM_RULE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ruleType").value("TERM"))
                .andExpect(jsonPath("$.term").value("취소 주문"))
                .andExpect(jsonPath("$.termAliases").value("취소된 주문, 주문 취소"))
                .andExpect(jsonPath("$.tableName").value("shop_order"))
                .andExpect(jsonPath("$.columnName").value("order_status"))
                .andExpect(jsonPath("$.relatedTableName").doesNotExist())
                .andExpect(jsonPath("$.autoInclude").value(false))
                .andExpect(jsonPath("$.valueItems", hasSize(2)))
                .andExpect(jsonPath("$.valueItems[1].code").value("C2"))
                .andExpect(jsonPath("$.valueItems[1].label").value("관리자 취소"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.targetStatus").value("OK"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    @DisplayName("목록은 id 순이고 type, table 필터를 대소문자 무시로 적용한다. table 은 relatedTableName 도 본다")
    void list_appliesFilters() throws Exception {
        createRule(FILTER_RULE);
        createRule(JOIN_RULE);
        createRule(MEMBER_PERIOD_RULE);

        mockMvc.perform(get(BASE, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].ruleType").value("TABLE_DEFAULT_FILTER"))
                .andExpect(jsonPath("$[1].valueItems", hasSize(0)));
        mockMvc.perform(get(BASE, projectId).param("type", "period_column"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tableName").value("shop_member"));
        mockMvc.perform(get(BASE, projectId).param("table", "SHOP_PAYMENT"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].ruleType").value("JOIN_WITH"));
        mockMvc.perform(get(BASE, projectId).param("type", "EXAMPLE_QUERY"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("지원하지 않는 type")));
    }

    @Test
    @DisplayName("단건 조회, 다른 프로젝트의 규칙이나 없는 규칙은 404, 없는 프로젝트는 400")
    void get_singleAndNotFound() throws Exception {
        Long ruleId = createRule(PERIOD_RULE);
        Long otherProject = projectService.createProject("br-other-" + System.nanoTime(), "o", null).getId();

        mockMvc.perform(get(BASE + "/{ruleId}", projectId, ruleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columnName").value("ordered_at"));
        mockMvc.perform(get(BASE + "/{ruleId}", otherProject, ruleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("업무 규칙을 찾을 수 없습니다: " + ruleId));
        mockMvc.perform(get(BASE, 999999L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("프로젝트를 찾을 수 없습니다: 999999"));
        mockMvc.perform(get(BASE + "/abc", projectId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT 은 전체 수정이며 enabled 를 끌 수 있다")
    void put_replacesAndToggles() throws Exception {
        Long ruleId = createRule(FILTER_RULE);

        mockMvc.perform(put(BASE + "/{ruleId}", projectId, ruleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order",
                                 "conditionText":"deleted_yn = 'N' AND order_status <> 'X'",
                                 "description":"테스트 주문 제외","enabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.conditionText").value("deleted_yn = 'N' AND order_status <> 'X'"))
                .andExpect(jsonPath("$.description").value("테스트 주문 제외"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(get(BASE + "/{ruleId}", projectId, ruleId))
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(put(BASE + "/{ruleId}", projectId, 999999L)
                        .contentType(MediaType.APPLICATION_JSON).content(FILTER_RULE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE 는 soft delete 후 204, 이후 조회 404 이고 목록에서 빠진다")
    void delete_softDeletes() throws Exception {
        Long ruleId = createRule(PERIOD_RULE);

        mockMvc.perform(delete(BASE + "/{ruleId}", projectId, ruleId)).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/{ruleId}", projectId, ruleId)).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE, projectId)).andExpect(jsonPath("$", hasSize(0)));
        assertThat(ruleRepository.findById(ruleId)).get()
                .satisfies(r -> {
                    assertThat(r.getIsDeleted()).isTrue();
                    assertThat(r.getDeletedAt()).isNotNull();
                });
        mockMvc.perform(delete(BASE + "/{ruleId}", projectId, ruleId)).andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            {"tableName":"shop_order","conditionText":"a = 1"}                                                  | ruleType은 필수입니다
            {"ruleType":"EXAMPLE_QUERY","tableName":"shop_order"}                                               | 지원하지 않는 ruleType입니다
            {"ruleType":"TERM","tableName":"shop_order","conditionText":"a = 1"}                                | term은(는) 필수입니다
            {"ruleType":"TERM","term":"t","tableName":"shop_order"}                                             | conditionText 또는 valueItems 중 하나가 필요합니다
            {"ruleType":"TERM","term":"t","tableName":"shop_order","valueItems":[{"code":"C1"}]}                | columnName이 필요합니다
            {"ruleType":"TABLE_DEFAULT_FILTER","conditionText":"a = 1"}                                         | tableName은(는) 필수입니다
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order"}                                        | conditionText은(는) 필수입니다
            {"ruleType":"PERIOD_COLUMN","tableName":"shop_order"}                                               | columnName은(는) 필수입니다
            {"ruleType":"JOIN_WITH","tableName":"shop_order"}                                                   | relatedTableName은(는) 필수입니다
            {"ruleType":"JOIN_WITH","tableName":"shop_order","relatedTableName":"SHOP_ORDER"}                   | relatedTableName은 tableName과 달라야 합니다
            {"ruleType":"COLUMN_VALUES","tableName":"shop_order","columnName":"order_status"}                   | valueItems는 필수입니다
            {"ruleType":"COLUMN_VALUES","tableName":"shop_order","columnName":"order_status","valueItems":[{"code":" ","label":"x"}]} | valueItems[0].code는 필수입니다
            {"ruleType":"COLUMN_VALUES","tableName":"shop_order","columnName":"order_status","valueItems":[{"code":"C1"},{"code":"C1"}]} | valueItems에 중복된 code가 있습니다: C1
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_x","conditionText":"a = 1"}                   | 테이블을 찾을 수 없습니다: shop_x
            {"ruleType":"PERIOD_COLUMN","tableName":"shop_order","columnName":"paid_at"}                        | 컬럼을 찾을 수 없습니다: shop_order.paid_at
            {"ruleType":"JOIN_WITH","tableName":"shop_order","relatedTableName":"shop_x"}                       | 함께 쓰는 테이블을 찾을 수 없습니다: shop_x
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"deleted_yn = 'N';"}   | 세미콜론
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"a = 1 -- x"}          | 주석 기호
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"a = 1 /* x"}          | 주석 기호
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"a = 1\\nb = 2"}       | conditionText에는 줄바꿈을 넣을 수 없습니다
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"a = 1","description":"x\\r\\ny"} | description에는 줄바꿈을 넣을 수 없습니다
            {"ruleType":"TABLE_DEFAULT_FILTER","tableName":"shop_order","conditionText":"a = 1","description":"end project business rules"} | 'PROJECT BUSINESS RULES' 문자열
            {"ruleType":"TERM","term":"t```","tableName":"shop_order","conditionText":"a = 1"}                  | 백틱 3개
            {"ruleType":"COLUMN_VALUES","tableName":"shop_order","columnName":"order_status","valueItems":[{"code":"C1","label":"a\\nb"}]} | valueItems[0].label에는 줄바꿈을 넣을 수 없습니다
            """)
    @DisplayName("형식, 유형별 필수 필드, 대상 존재 검증에 걸리면 400 과 오류 문구를 돌려주고 저장하지 않는다")
    void create_invalid_returns400(String json, String expectedMessage) throws Exception {
        postRule(json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString(expectedMessage)));
        assertThat(ruleRepository.findAllByProjectIdAndIsDeletedFalseOrderByIdAsc(projectId)).isEmpty();
    }

    @Test
    @DisplayName("길이 상한을 넘으면 400")
    void create_tooLong_returns400() throws Exception {
        String longTerm = "가".repeat(101);
        postRule("{\"ruleType\":\"TERM\",\"term\":\"" + longTerm + "\",\"tableName\":\"shop_order\",\"conditionText\":\"a = 1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("term은(는) 100자를 넘을 수 없습니다"));
        postRule("{\"ruleType\":\"TABLE_DEFAULT_FILTER\",\"tableName\":\"shop_order\",\"conditionText\":\"" + "a".repeat(1001) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("conditionText은(는) 1000자를 넘을 수 없습니다"));

        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            items.append(i == 0 ? "" : ",").append("{\"code\":\"V").append(i).append("\"}");
        }
        postRule("{\"ruleType\":\"COLUMN_VALUES\",\"tableName\":\"shop_order\",\"columnName\":\"order_status\",\"valueItems\":[" + items + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("valueItems는 50개를 넘을 수 없습니다"));
    }

    @Test
    @DisplayName("본문 JSON 형식 오류는 500 이 아니라 400")
    void create_malformedJson_returns400() throws Exception {
        postRule("{\"ruleType\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 본문 형식이 올바르지 않습니다"));
        postRule("{\"ruleType\":\"TERM\",\"valueItems\":\"C1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("요청 본문 형식이 올바르지 않습니다"));
    }

    @Test
    @DisplayName("파일 삭제와 재업로드로 대상이 사라지면 규칙은 남고 targetStatus 가 바뀐다. 다시 올리면 OK 로 돌아온다")
    void targetStatus_reflectsCurrentMetadata() throws Exception {
        Long joinId = createRule(JOIN_RULE);
        Long periodId = createRule(PERIOD_RULE);
        Long memberId = createRule(MEMBER_PERIOD_RULE);

        // shop_payment 가 든 파일 삭제 -> JOIN_WITH 의 related 테이블 없음
        mockMvc.perform(delete("/api/v1/projects/{projectId}/files/{fileId}", projectId, fileId("payment.xlsx")))
                .andExpect(status().isNoContent());
        // shop_order 를 ordered_at 없이 재업로드 -> PERIOD_COLUMN 컬럼 없음
        upload("order-v2.xlsx", ExcelFixtures.standardKorean(
                new Object[]{"shop_order", "주문", 1, "order_id", "", "bigint", "20", "PRI", "NO", "", ""},
                new Object[]{"shop_order", "주문", 2, "order_status", "", "varchar", "2", "", "NO", "", ""}));
        // shop_member 파일 삭제 -> 테이블 없음
        mockMvc.perform(delete("/api/v1/projects/{projectId}/files/{fileId}", projectId, fileId("member.xlsx")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(joinId))
                .andExpect(jsonPath("$[0].targetStatus").value("RELATED_TABLE_MISSING"))
                .andExpect(jsonPath("$[1].id").value(periodId))
                .andExpect(jsonPath("$[1].targetStatus").value("COLUMN_MISSING"))
                .andExpect(jsonPath("$[2].id").value(memberId))
                .andExpect(jsonPath("$[2].targetStatus").value("TABLE_MISSING"));

        // 대상이 사라진 규칙도 대상 이름을 바꾸지 않으면 수정(비활성화)할 수 있다
        mockMvc.perform(put(BASE + "/{ruleId}", projectId, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MEMBER_PERIOD_RULE.replace("}", ",\"enabled\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.targetStatus").value("TABLE_MISSING"));
        // 대상 이름을 없는 테이블로 바꾸는 수정은 400
        mockMvc.perform(put(BASE + "/{ruleId}", projectId, joinId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOIN_RULE.replace("shop_payment\",", "shop_refund\",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("함께 쓰는 테이블을 찾을 수 없습니다: shop_refund"));

        // shop_payment 를 다시 올리면 OK 로 돌아온다 (상태를 저장하지 않으므로 동기화 불필요)
        upload("payment-v2.xlsx", ExcelFixtures.standardKorean(
                new Object[]{"shop_payment", "결제", 1, "payment_id", "", "bigint", "20", "PRI", "NO", "", ""}));
        mockMvc.perform(get(BASE + "/{ruleId}", projectId, joinId))
                .andExpect(jsonPath("$.targetStatus").value("OK"));
    }

    @Test
    @DisplayName("프로젝트 초기화 시 업무 규칙도 soft delete 되어 목록이 0건이 된다 (D6)")
    void reset_softDeletesRules() throws Exception {
        createRule(FILTER_RULE);
        createRule(JOIN_RULE);
        Long otherProject = projectService.createProject("br-keep-" + System.nanoTime(), "k", null).getId();
        mockMvc.perform(multipart("/api/v1/projects/{projectId}/schemas:import", otherProject)
                        .file(new MockMultipartFile("files", "o.xlsx", "application/octet-stream",
                                ExcelFixtures.standardKorean(new Object[]{"shop_order", "주문", 1, "order_id", "", "bigint", "20", "PRI", "NO", "", ""}))))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE, otherProject).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleType\":\"TABLE_DEFAULT_FILTER\",\"tableName\":\"shop_order\",\"conditionText\":\"a = 1\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/{projectId}/reset", projectId)).andExpect(status().isNoContent());

        mockMvc.perform(get(BASE, projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(ruleRepository.findAll()).filteredOn(r -> r.getProjectId().equals(projectId))
                .hasSize(2)
                .allSatisfy(r -> {
                    assertThat(r.getIsDeleted()).isTrue();
                    assertThat(r.getDeletedAt()).isNotNull();
                });
        // 다른 프로젝트의 규칙은 그대로
        mockMvc.perform(get(BASE, otherProject)).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("preview: 용어/별칭 매칭 테이블 강제 포함, JOIN_WITH 자동 포함, 선택 테이블 규칙만 주입")
    void preview_selectsRules() throws Exception {
        Long termId = createRule(TERM_RULE);
        Long filterId = createRule(FILTER_RULE);
        Long joinId = createRule(JOIN_RULE);
        createRule(MEMBER_PERIOD_RULE);
        Long disabledId = createRule(PERIOD_RULE.replace("}", ",\"enabled\":false}"));

        mockMvc.perform(post(BASE + "/preview", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"지난달 취소된주문 목록\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedTerms", hasSize(1)))
                .andExpect(jsonPath("$.matchedTerms[0].ruleId").value(termId))
                .andExpect(jsonPath("$.matchedTerms[0].matchedText").value("취소된 주문"))
                .andExpect(jsonPath("$.forcedTables[0]").value("shop_order"))
                .andExpect(jsonPath("$.autoIncludedTables[0]").value("shop_payment"))
                .andExpect(jsonPath("$.selectedTables", hasSize(2)))
                .andExpect(jsonPath("$.rules[*].id").value(org.hamcrest.Matchers.contains(
                        termId.intValue(), filterId.intValue(), joinId.intValue())))
                .andExpect(jsonPath("$.skippedRuleCount").value(0));

        // 질의에 용어가 없으면 tables 로 준 테이블 기준. 없는 이름은 무시하고 표기는 메타데이터 기준.
        mockMvc.perform(post(BASE + "/preview", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"회원 수\",\"tables\":[\"SHOP_MEMBER\",\"shop_x\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedTerms", hasSize(0)))
                .andExpect(jsonPath("$.selectedTables").value(org.hamcrest.Matchers.contains("shop_member")))
                .andExpect(jsonPath("$.rules", hasSize(1)))
                .andExpect(jsonPath("$.rules[0].tableName").value("shop_member"));

        assertThat(disabledId).isNotNull();
        mockMvc.perform(post(BASE + "/preview", projectId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("query는 필수입니다"));
    }
}
