package com.autoerd.domain.sql.port;

/**
 * Step1(관련 테이블 판별) 호출 자체가 실패했음을 나타낸다.
 * "관련 테이블 없음"(빈 목록 반환)과 구분하기 위해 사용한다.
 */
public class SqlTableResolutionException extends RuntimeException {

    public SqlTableResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
