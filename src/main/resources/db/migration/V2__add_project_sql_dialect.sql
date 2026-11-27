-- ============================================================
-- V2: 프로젝트별 SQL 방언 (이슈 #18)
-- 기존 행은 DEFAULT 'MYSQL' 로 채워져 하위 호환을 유지한다.
-- 허용 값(MSSQL | MYSQL | POSTGRESQL)은 애플리케이션 enum 으로 검증하며 CHECK 제약은 두지 않는다.
-- ============================================================
ALTER TABLE metadata_project
    ADD COLUMN sql_dialect VARCHAR(20) NOT NULL DEFAULT 'MYSQL'
        COMMENT 'SQL 생성 대상 방언: MSSQL | MYSQL | POSTGRESQL'
        AFTER description;
