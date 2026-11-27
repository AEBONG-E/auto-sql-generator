package com.autoerd.infrastructure.llm;

import com.autoerd.domain.sql.model.SqlTableResolutionRequest;
import com.autoerd.domain.sql.port.SqlTableResolutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class LlmSqlTableResolverAdapterTest {

    private ChatClient chatClient;
    private LlmSqlTableResolverAdapter adapter;
    private final SqlTableResolutionRequest request =
            new SqlTableResolutionRequest(1L, "주문 목록", List.of("orders", "members"));

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        adapter = new LlmSqlTableResolverAdapter(chatClient);
    }

    @Test
    void LLM_호출이_실패하면_빈_목록이_아니라_SqlTableResolutionException을_던진다() {
        RuntimeException cause = new RuntimeException("I/O error: Connection refused");
        when(chatClient.prompt().messages(anyList()).call().content()).thenThrow(cause);

        assertThatThrownBy(() -> adapter.resolveRelevantTableNames(request))
                .isInstanceOf(SqlTableResolutionException.class)
                .hasCause(cause);
    }

    @Test
    void LLM이_NONE을_응답하면_빈_목록을_반환한다() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("NONE");

        assertThat(adapter.resolveRelevantTableNames(request)).isEmpty();
    }

    @Test
    @DisplayName("설명이 있는 후보는 'name [description]' 형식으로 Step1 프롬프트에 포함된다")
    void resolve_withDescription_includesDescriptionInPrompt() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("tb_order");
        SqlTableResolutionRequest request = new SqlTableResolutionRequest(
                1L, "주문 목록", List.of("tb_order", "tb_member"),
                Map.of("tb_order", "주문", "tb_member", "회원"));

        List<String> result = adapter.resolveRelevantTableNames(request);

        String userContent = captureUserContent();
        assertThat(userContent).contains("tb_order [주문]\ntb_member [회원]");
        assertThat(result).containsExactly("tb_order");
    }

    @Test
    @DisplayName("설명이 없거나 공백인 후보는 이름만 Step1 프롬프트에 포함된다")
    void resolve_withoutDescription_includesNameOnly() {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn("tb_log, tb_code, tb_unknown");
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("tb_code", "   ");
        SqlTableResolutionRequest request = new SqlTableResolutionRequest(
                1L, "로그", List.of("tb_log", "tb_code"), descriptions);

        List<String> result = adapter.resolveRelevantTableNames(request);

        String userContent = captureUserContent();
        assertThat(userContent).contains("tb_log\ntb_code\n");
        assertThat(userContent).doesNotContain("[");
        assertThat(result).containsExactly("tb_log", "tb_code");
    }

    // --- 이슈 #16: schema.table 형식 테이블명 매핑 ---

    private static final List<String> SCHEMA_QUALIFIED = List.of(
            "shop.group", "shop.purchase_order", "shop.guest-visitor", "audit.group", "tb_code");

    private List<String> resolveWith(String llmResponse, List<String> candidates) {
        when(chatClient.prompt().messages(anyList()).call().content()).thenReturn(llmResponse);
        return adapter.resolveRelevantTableNames(new SqlTableResolutionRequest(1L, "질의", candidates));
    }

    @Test
    @DisplayName("LLM 이 schema.table 후보명을 그대로 답하면 원래 이름으로 선택된다")
    void resolve_schemaQualifiedAnswer_keepsOriginalName() {
        assertThat(resolveWith("shop.group,shop.purchase_order", SCHEMA_QUALIFIED))
                .containsExactly("shop.group", "shop.purchase_order");
    }

    @Test
    @DisplayName("하이픈이 들어간 schema.table 이름도 유지된다")
    void resolve_hyphenatedName_keepsOriginalName() {
        assertThat(resolveWith("shop.guest-visitor", SCHEMA_QUALIFIED))
                .containsExactly("shop.guest-visitor");
    }

    @Test
    @DisplayName("대소문자가 달라도 원래 이름으로 매핑된다")
    void resolve_caseDifference_mapsToOriginalName() {
        assertThat(resolveWith("SHOP.GROUP, Shop.Purchase_Order", SCHEMA_QUALIFIED))
                .containsExactly("shop.group", "shop.purchase_order");
        assertThat(resolveWith("TB_CODE", SCHEMA_QUALIFIED))
                .containsExactly("tb_code");
    }

    @Test
    @DisplayName("따옴표, 백틱, 대괄호로 감싼 이름도 원래 이름으로 매핑된다")
    void resolve_quotedOrBracketed_mapsToOriginalName() {
        assertThat(resolveWith("`shop`.`group`, \"shop\".\"purchase_order\", [shop].[guest-visitor], 'tb_code'",
                SCHEMA_QUALIFIED))
                .containsExactly("shop.group", "shop.purchase_order", "shop.guest-visitor", "tb_code");
    }

    @Test
    @DisplayName("스키마 없이 테이블명만 답하면 해당 schema.table 후보로 매핑된다")
    void resolve_tableOnlyAnswer_mapsToSchemaQualifiedCandidate() {
        assertThat(resolveWith("purchase_order", SCHEMA_QUALIFIED))
                .containsExactly("shop.purchase_order");
    }

    @Test
    @DisplayName("스키마 없이 답한 이름이 여러 스키마에 있으면 일치하는 후보를 모두 포함한다")
    void resolve_tableOnlyAnswerInMultipleSchemas_includesAllMatches() {
        assertThat(resolveWith("group", SCHEMA_QUALIFIED))
                .containsExactlyInAnyOrder("shop.group", "audit.group");
    }

    @Test
    @DisplayName("정의서가 테이블명만 있는 형식이면 LLM 이 schema.table 로 답해도 테이블명으로 매핑된다")
    void resolve_schemaQualifiedAnswerForTableOnlyCandidates_mapsToTableName() {
        assertThat(resolveWith("public.tb_order, tb_member", List.of("tb_order", "tb_member", "tb_code")))
                .containsExactly("tb_order", "tb_member");
    }

    @Test
    @DisplayName("다른 스키마로 답한 이름은 schema.table 후보에 잘못 매핑되지 않는다")
    void resolve_wrongSchema_isNotMapped() {
        assertThat(resolveWith("billing.group", SCHEMA_QUALIFIED)).isEmpty();
    }

    @Test
    @DisplayName("후보에 없는 이름은 버리고 중복은 한 번만 남긴다")
    void resolve_unknownAndDuplicate_filtered() {
        assertThat(resolveWith("shop.group, unknown_table, SHOP.GROUP, shop.group.", SCHEMA_QUALIFIED))
                .containsExactly("shop.group");
    }

    @SuppressWarnings("unchecked")
    private String captureUserContent() {
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(chatClient.prompt(), atLeastOnce()).messages(captor.capture());
        return captor.getValue().stream()
                .filter(UserMessage.class::isInstance)
                .map(Message::getText)
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("Step1 LLM 호출 실패 로그에 질의 원문과 프롬프트가 남지 않는다")
    void resolve_failure_doesNotLogQueryOrPrompt(CapturedOutput output) {
        when(chatClient.prompt().messages(anyList()).call().content())
                .thenThrow(new RuntimeException("I/O error: Connection refused"));

        assertThatThrownBy(() -> adapter.resolveRelevantTableNames(request))
                .isInstanceOf(SqlTableResolutionException.class);

        assertThat(output).contains("Table resolution LLM call failed: type=java.lang.RuntimeException");
        assertThat(output).doesNotContain("주문 목록");
        assertThat(output).doesNotContain("Database tables:");
    }
}
