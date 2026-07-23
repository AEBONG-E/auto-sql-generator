package com.autoerd.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Step1 후보 사전 축소(pre-filter) 설정.
 *
 * <p>{@code app.llm.prefilter.*}로 바인딩된다. {@link #synonyms}는 한글 질의 용어 →
 * 영문 식별자 토큰 매핑으로, 한글 질의("지점")가 영문 테이블명({@code branch})에 매칭되지 않아
 * 관련 테이블이 후보에서 밀리는 문제(QA 실측 Q1)를 보완한다.
 *
 * <p>{@link #synonyms}에는 코드 기본값이 미리 채워져 있고, {@code application.yml}에서 같은 prefix로
 * 항목을 추가/재정의하면 병합된다(맵 바인딩은 기존 키를 지우지 않고 추가·덮어씀).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.llm.prefilter")
public class PrefilterProperties {

    /** 필터 on/off. 기본 true. */
    private boolean enabled = true;

    /** 테이블 수가 이 값 이하이면 사전 축소를 적용하지 않는다(소형 스키마 회귀 방지). */
    private int minTables = 30;

    /**
     * 후보 상한의 절대 하한선. 정확도 최우선 정책에 따라 기본 50으로 상향.
     * 실효 상한 = max(maxCandidates, ceil(전체 × candidateRatio)).
     */
    private int maxCandidates = 50;

    /**
     * 후보 상한의 비율 기반 하한선(전체 대비). 정확도 최우선 정책: 기본 0.7(70% 유지).
     * 90테이블이면 max(50, 63) = 63개까지 후보 유지. 0~1로 클램프된다.
     */
    private double candidateRatio = 0.7;

    /**
     * 관계 1-hop 이웃을 스코어와 무관하게 후보에 포함할지 여부(JOIN 브리지/코어 FK 누락 방지).
     * 정확도 최우선: 기본 true. 이웃 포함은 실효 상한을 넘어 전체 크기까지 허용된다.
     */
    private boolean expandRelations = true;

    /**
     * 한글 질의 용어 → 영문 식별자 토큰(공백 구분) 동의어 사전.
     * 예: {@code "지점" -> "branch"} 이면 질의에 "지점"이 포함될 때 "branch" 토큰을 매칭에 추가한다.
     * 코드 기본값은 member/커머스 도메인 공통 용어로 소량 시드하고, yml에서 확장 가능.
     */
    private Map<String, String> synonyms = defaultSynonyms();

    private static Map<String, String> defaultSynonyms() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("지점", "branch");
        m.put("점포", "branch store");
        m.put("회원", "user member");
        m.put("사용자", "user");
        m.put("고객", "customer user");
        m.put("계정", "account user");
        m.put("주문", "order");
        m.put("상품", "product item goods");
        m.put("제품", "product");
        m.put("결제", "payment pay");
        m.put("배송", "delivery shipping ship");
        m.put("카테고리", "category");
        m.put("등급", "grade level rank");
        m.put("이력", "history log hist");
        m.put("약관", "agreement terms");
        m.put("동의", "agreement agree consent");
        m.put("후원", "sponsor");
        m.put("법인", "corporation corp company");
        m.put("감사", "audit");
        m.put("권한", "role permission auth");
        m.put("지역", "region area");
        m.put("정산", "settlement");
        m.put("포인트", "point");
        m.put("쿠폰", "coupon");
        m.put("재고", "stock inventory");
        return m;
    }
}
