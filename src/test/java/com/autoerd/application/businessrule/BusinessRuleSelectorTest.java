package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleTargetStatus;
import com.autoerd.domain.businessrule.model.BusinessRuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이슈 #24: preview 용 최소 선택 로직 (W2 에서 확장).
 */
class BusinessRuleSelectorTest {

    private static BusinessRuleResponse rule(long id, BusinessRuleType type, String table, String related,
                                             boolean autoInclude, boolean enabled, BusinessRuleTargetStatus status) {
        return new BusinessRuleResponse(id, type, type == BusinessRuleType.TERM ? "취소 주문" : null, null,
                table, null, related, "a = 1", List.of(), autoInclude, null, enabled, status, null, null);
    }

    private static BusinessRuleResponse join(long id, String table, String related) {
        return rule(id, BusinessRuleType.JOIN_WITH, table, related, true, true, BusinessRuleTargetStatus.OK);
    }

    @Test
    @DisplayName("JOIN_WITH 자동 포함은 1-hop 이고 최대 3개다")
    void autoInclude_isOneHopAndCapped() {
        List<BusinessRuleResponse> rules = List.of(
                join(1, "shop_order", "t1"),
                join(2, "shop_order", "t2"),
                join(3, "t1", "t9"),          // 1-hop 이 아니므로 제외
                join(4, "shop_order", "t3"),
                join(5, "shop_order", "t4")); // 상한 초과

        BusinessRulePreviewResponse result = BusinessRuleSelector.select(rules, "x", List.of("shop_order"));

        assertThat(result.autoIncludedTables()).containsExactly("t1", "t2", "t3");
        assertThat(result.selectedTables()).containsExactly("shop_order", "t1", "t2", "t3");
        assertThat(result.rules()).extracting(BusinessRuleResponse::id).containsExactly(1L, 2L, 4L);
    }

    @Test
    @DisplayName("비활성 규칙과 대상 없음 규칙은 매칭/주입에서 빠지고, 대상 없음만 skippedRuleCount 에 센다")
    void disabledAndMissingAreExcluded() {
        List<BusinessRuleResponse> rules = List.of(
                rule(1, BusinessRuleType.TERM, "shop_order", null, false, false, BusinessRuleTargetStatus.OK),
                rule(2, BusinessRuleType.TERM, "shop_order", null, false, true, BusinessRuleTargetStatus.TABLE_MISSING),
                rule(3, BusinessRuleType.JOIN_WITH, "shop_order", "shop_payment", false, true,
                        BusinessRuleTargetStatus.OK));

        BusinessRulePreviewResponse result = BusinessRuleSelector.select(rules, "취소 주문", List.of("shop_order"));

        assertThat(result.matchedTerms()).isEmpty();
        assertThat(result.autoIncludedTables()).isEmpty();
        // JOIN_WITH 는 related 가 선택되지 않았으므로 주입하지 않는다
        assertThat(result.rules()).isEmpty();
        assertThat(result.skippedRuleCount()).isEqualTo(1);
    }
}
