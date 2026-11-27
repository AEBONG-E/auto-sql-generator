package com.autoerd.application.businessrule;

import java.util.List;

/**
 * 미리보기 요청 (LLM 호출 없음).
 *
 * @param query  사용자 질의 (필수)
 * @param tables Step1 결과를 대신하는 테이블 목록 (선택). 현재 메타데이터에 없는 이름은 무시한다.
 */
public record BusinessRulePreviewRequest(String query, List<String> tables) {
}
