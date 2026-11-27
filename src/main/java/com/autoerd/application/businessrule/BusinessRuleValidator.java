package com.autoerd.application.businessrule;

import com.autoerd.domain.businessrule.model.BusinessRuleType;
import com.autoerd.exception.AppException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 업무 규칙 요청의 형식 검증과 정규화 (이슈 #24 설계 4.1). 테이블/컬럼 존재 검증은 서비스가 한다.
 *
 * <p>조건식은 실행하지 않고 프롬프트에만 쓰므로 SQL 문법이나 DML/DDL 키워드는 검사하지 않는다.
 * 목적은 프롬프트 블록 구조 보호(한 줄 제한, 블록 구분 문자열 금지)와 세미콜론/주석 기호로 인한
 * 출력 판정 혼선 방지다. 실패하면 400 (AppException.badRequest).
 */
final class BusinessRuleValidator {

    static final int MAX_TERM = 100;
    static final int MAX_TERM_ALIASES = 300;
    static final int MAX_NAME = 255;
    static final int MAX_CONDITION = 1000;
    static final int MAX_DESCRIPTION = 1000;
    static final int MAX_VALUE_CODE = 100;
    static final int MAX_VALUE_LABEL = 200;
    static final int MAX_VALUE_ITEMS = 50;

    /** 프롬프트 규칙 블록의 시작/끝 구분 문자열 (설계 6.3). 대소문자 무시로 금지한다. */
    static final String BLOCK_MARKER = "PROJECT BUSINESS RULES";
    private static final String TRIPLE_BACKTICK = "```";
    private static final List<String> COMMENT_MARKERS = List.of("--", "/*", "*/");

    private BusinessRuleValidator() {
    }

    static BusinessRuleType parseType(String value, String fieldName) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(BusinessRuleType.values())
                .filter(t -> t.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> AppException.badRequest(
                        "지원하지 않는 " + fieldName + "입니다. 허용 값: " + Arrays.toString(BusinessRuleType.values())));
    }

    static BusinessRuleDraft validate(BusinessRuleRequest request) {
        if (request == null) {
            throw AppException.badRequest("요청 본문이 필요합니다");
        }
        String rawType = trimToNull(request.ruleType());
        if (rawType == null) {
            throw AppException.badRequest("ruleType은 필수입니다");
        }
        BusinessRuleType type = parseType(rawType, "ruleType");

        String term = trimToNull(request.term());
        String termAliases = normalizeAliases(request.termAliases());
        String tableName = trimToNull(request.tableName());
        String columnName = trimToNull(request.columnName());
        String relatedTableName = trimToNull(request.relatedTableName());
        String conditionText = trimToNull(request.conditionText());
        List<BusinessRuleValueItem> valueItems = normalizeValueItems(request.valueItems());
        boolean autoInclude = Boolean.TRUE.equals(request.autoInclude());
        String description = trimToNull(request.description());
        boolean enabled = request.enabled() == null || request.enabled();

        // 유형별 필수 필드와, 그 유형에 쓰이지 않는 필드 제거
        require(tableName, "tableName");
        switch (type) {
            case TERM -> {
                require(term, "term");
                if (conditionText == null && valueItems.isEmpty()) {
                    throw AppException.badRequest("TERM 규칙은 conditionText 또는 valueItems 중 하나가 필요합니다");
                }
                if (!valueItems.isEmpty() && columnName == null) {
                    throw AppException.badRequest("valueItems를 쓰는 TERM 규칙은 columnName이 필요합니다");
                }
                relatedTableName = null;
                autoInclude = false;
            }
            case TABLE_DEFAULT_FILTER -> {
                require(conditionText, "conditionText");
                term = null;
                termAliases = null;
                columnName = null;
                relatedTableName = null;
                valueItems = List.of();
                autoInclude = false;
            }
            case PERIOD_COLUMN -> {
                require(columnName, "columnName");
                term = null;
                termAliases = null;
                relatedTableName = null;
                conditionText = null;
                valueItems = List.of();
                autoInclude = false;
            }
            case JOIN_WITH -> {
                require(relatedTableName, "relatedTableName");
                if (MetadataNameIndex.key(relatedTableName).equals(MetadataNameIndex.key(tableName))) {
                    throw AppException.badRequest("relatedTableName은 tableName과 달라야 합니다");
                }
                term = null;
                termAliases = null;
                columnName = null;
                valueItems = List.of();
            }
            case COLUMN_VALUES -> {
                require(columnName, "columnName");
                if (valueItems.isEmpty()) {
                    throw AppException.badRequest("valueItems는 필수입니다");
                }
                term = null;
                termAliases = null;
                relatedTableName = null;
                conditionText = null;
                autoInclude = false;
            }
            default -> throw AppException.badRequest("지원하지 않는 ruleType입니다");
        }

        // 길이
        maxLength(term, MAX_TERM, "term");
        maxLength(termAliases, MAX_TERM_ALIASES, "termAliases");
        maxLength(tableName, MAX_NAME, "tableName");
        maxLength(columnName, MAX_NAME, "columnName");
        maxLength(relatedTableName, MAX_NAME, "relatedTableName");
        maxLength(conditionText, MAX_CONDITION, "conditionText");
        maxLength(description, MAX_DESCRIPTION, "description");

        // 형식 안전성
        safeText(term, "term");
        safeText(termAliases, "termAliases");
        safeText(description, "description");
        safeText(conditionText, "conditionText");
        if (conditionText != null) {
            if (conditionText.contains(";")) {
                throw AppException.badRequest("conditionText에는 세미콜론(;)을 넣을 수 없습니다");
            }
            for (String marker : COMMENT_MARKERS) {
                if (conditionText.contains(marker)) {
                    throw AppException.badRequest("conditionText에는 주석 기호(--, /*, */)를 넣을 수 없습니다");
                }
            }
        }
        validateValueItems(valueItems);

        return new BusinessRuleDraft(type, term, termAliases, tableName, columnName, relatedTableName,
                conditionText, valueItems, autoInclude, description, enabled);
    }

    private static void validateValueItems(List<BusinessRuleValueItem> items) {
        if (items.size() > MAX_VALUE_ITEMS) {
            throw AppException.badRequest("valueItems는 " + MAX_VALUE_ITEMS + "개를 넘을 수 없습니다");
        }
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            BusinessRuleValueItem item = items.get(i);
            String field = "valueItems[" + i + "]";
            if (item.code() == null) {
                throw AppException.badRequest(field + ".code는 필수입니다");
            }
            maxLength(item.code(), MAX_VALUE_CODE, field + ".code");
            maxLength(item.label(), MAX_VALUE_LABEL, field + ".label");
            safeText(item.code(), field + ".code");
            safeText(item.label(), field + ".label");
            if (!codes.add(item.code())) {
                throw AppException.badRequest("valueItems에 중복된 code가 있습니다: " + item.code());
            }
        }
    }

    /** null 항목은 버리고 code/label 을 trim 한다. code 가 비어 있으면 null 로 두어 검증에서 400 이 된다. */
    private static List<BusinessRuleValueItem> normalizeValueItems(List<BusinessRuleValueItem> items) {
        if (items == null) {
            return List.of();
        }
        List<BusinessRuleValueItem> result = new ArrayList<>();
        for (BusinessRuleValueItem item : items) {
            if (item != null) {
                result.add(new BusinessRuleValueItem(trimToNull(item.code()), trimToNull(item.label())));
            }
        }
        return List.copyOf(result);
    }

    /** 쉼표로 나눠 각 별칭을 trim 하고 빈 항목을 버린 뒤 ", " 로 다시 잇는다. */
    private static String normalizeAliases(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        List<String> aliases = Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return aliases.isEmpty() ? null : String.join(", ", aliases);
    }

    private static void safeText(String value, String field) {
        if (value == null) {
            return;
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw AppException.badRequest(field + "에는 줄바꿈을 넣을 수 없습니다");
        }
        if (value.contains(TRIPLE_BACKTICK)) {
            throw AppException.badRequest(field + "에는 백틱 3개(```)를 넣을 수 없습니다");
        }
        if (value.toUpperCase(Locale.ROOT).contains(BLOCK_MARKER)) {
            throw AppException.badRequest(field + "에는 '" + BLOCK_MARKER + "' 문자열을 넣을 수 없습니다");
        }
    }

    private static void require(String value, String field) {
        if (value == null) {
            throw AppException.badRequest(field + "은(는) 필수입니다");
        }
    }

    private static void maxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw AppException.badRequest(field + "은(는) " + max + "자를 넘을 수 없습니다");
        }
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
