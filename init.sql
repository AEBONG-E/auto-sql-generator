-- Atlas Metadata Foundation
-- MySQL 8.x init schema draft
-- Policy:
-- 1) RDBMS: MySQL 8.x
-- 2) Soft delete
-- 3) Relations are materialized and stored at upload time
-- 4) Latest uploaded table version becomes current
-- 5) Flyway will eventually manage this script as V1__init_metadata_foundation.sql

CREATE DATABASE IF NOT EXISTS atlas_metadata
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE atlas_metadata;

SET NAMES utf8mb4;

CREATE TABLE metadata_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '프로젝트 PK',
    project_key VARCHAR(100) NOT NULL COMMENT '프로젝트 식별 키',
    project_name VARCHAR(200) NOT NULL COMMENT '프로젝트 명',
    project_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, ARCHIVED',
    description VARCHAR(1000) NULL COMMENT '프로젝트 설명',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    deleted_at DATETIME(6) NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    UNIQUE KEY uk_metadata_project_project_key (project_key),
    KEY idx_metadata_project_status (project_status, deleted_at),
    CONSTRAINT ck_metadata_project_soft_delete
        CHECK (
            (is_deleted = 0 AND deleted_at IS NULL)
            OR
            (is_deleted = 1 AND deleted_at IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='메타데이터 프로젝트';

CREATE TABLE metadata_source_file (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '소스 파일 PK',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '프로젝트 PK',
    original_filename VARCHAR(255) NOT NULL COMMENT '업로드 원본 파일명',
    stored_filename VARCHAR(255) NULL COMMENT '내부 저장 파일명 또는 오브젝트 키',
    upload_checksum CHAR(64) NULL COMMENT 'SHA-256 체크섬',
    source_type VARCHAR(30) NOT NULL DEFAULT 'XLSX' COMMENT 'XLSX, DB_SYNC, MANUAL',
    import_status VARCHAR(30) NOT NULL DEFAULT 'IMPORTED' COMMENT 'IMPORTED, FAILED, ROLLED_BACK',
    header_signature VARCHAR(500) NULL COMMENT '헤더 구조 식별 문자열',
    table_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '파일 내 테이블 수',
    column_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '파일 내 컬럼 수',
    relation_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '파일 내 관계 수',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '업로드 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    deleted_at DATETIME(6) NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_metadata_source_file_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    KEY idx_metadata_source_file_project (project_id, deleted_at),
    KEY idx_metadata_source_file_uploaded_at (uploaded_at),
    KEY idx_metadata_source_file_checksum (upload_checksum),
    CONSTRAINT ck_metadata_source_file_soft_delete
        CHECK (
            (is_deleted = 0 AND deleted_at IS NULL)
            OR
            (is_deleted = 1 AND deleted_at IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='업로드 원본 파일 메타데이터';

CREATE TABLE metadata_table (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '테이블 메타 PK',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '프로젝트 PK',
    source_file_id BIGINT UNSIGNED NOT NULL COMMENT '소스 파일 PK',
    schema_name VARCHAR(255) NOT NULL DEFAULT 'default' COMMENT '논리 스키마명, 예약어는 애플리케이션에서 백틱 포함 정규화',
    table_name VARCHAR(255) NOT NULL COMMENT '물리 테이블명',
    table_description VARCHAR(1000) NULL COMMENT '테이블 설명',
    current_version_no INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '동일 table_name 기준 버전 번호',
    is_current TINYINT(1) NOT NULL DEFAULT 1 COMMENT '최신 활성 버전 여부',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    active_table_name VARCHAR(255)
        GENERATED ALWAYS AS (
            CASE
                WHEN is_deleted = 0 AND is_current = 1 THEN CONCAT(schema_name, '.', table_name)
                ELSE NULL
            END
        ) STORED COMMENT '현재 활성 테이블 유니크 보장용',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    deleted_at DATETIME(6) NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_metadata_table_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_metadata_table_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    UNIQUE KEY uk_metadata_table_current (project_id, active_table_name),
    KEY idx_metadata_table_lookup (project_id, schema_name, table_name, is_current, is_deleted),
    KEY idx_metadata_table_source_file (source_file_id, is_deleted),
    CONSTRAINT ck_metadata_table_soft_delete
        CHECK (
            (is_deleted = 0 AND deleted_at IS NULL)
            OR
            (is_deleted = 1 AND deleted_at IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='테이블 메타데이터 버전';

CREATE TABLE metadata_column (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '컬럼 메타 PK',
    table_id BIGINT UNSIGNED NOT NULL COMMENT '테이블 메타 PK',
    ordinal_position INT UNSIGNED NOT NULL COMMENT '컬럼 순서',
    column_name VARCHAR(255) NOT NULL COMMENT '컬럼명',
    column_comment VARCHAR(1000) NULL COMMENT '컬럼 설명',
    data_type VARCHAR(100) NOT NULL COMMENT 'DATA TYPE',
    column_type VARCHAR(255) NULL COMMENT 'COLUMN TYPE 원문',
    key_type VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'PRI, UNI, MUL, NONE',
    nullable_yn CHAR(1) NOT NULL DEFAULT 'Y' COMMENT 'Y/N',
    auto_increment_yn CHAR(1) NOT NULL DEFAULT 'N' COMMENT 'Y/N',
    default_value VARCHAR(1000) NULL COMMENT '기본값',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    deleted_at DATETIME(6) NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_metadata_column_table
        FOREIGN KEY (table_id) REFERENCES metadata_table (id),
    UNIQUE KEY uk_metadata_column_table_name (table_id, column_name),
    KEY idx_metadata_column_table_ordinal (table_id, ordinal_position),
    KEY idx_metadata_column_name (column_name),
    CONSTRAINT ck_metadata_column_soft_delete
        CHECK (
            (is_deleted = 0 AND deleted_at IS NULL)
            OR
            (is_deleted = 1 AND deleted_at IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='컬럼 메타데이터';

CREATE TABLE metadata_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '관계 메타 PK',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '프로젝트 PK',
    source_file_id BIGINT UNSIGNED NOT NULL COMMENT '관계 생성 파일 PK',
    from_table_id BIGINT UNSIGNED NOT NULL COMMENT '출발 테이블 PK',
    from_column_id BIGINT UNSIGNED NOT NULL COMMENT '출발 컬럼 PK',
    to_table_id BIGINT UNSIGNED NOT NULL COMMENT '도착 테이블 PK',
    to_column_id BIGINT UNSIGNED NOT NULL COMMENT '도착 컬럼 PK',
    relation_type VARCHAR(30) NOT NULL COMMENT 'ONE_TO_MANY, MANY_TO_MANY, SELF_REFERENCE',
    inference_source VARCHAR(30) NOT NULL DEFAULT 'HEURISTIC' COMMENT 'HEURISTIC, MANUAL, DB_SYNC',
    confidence_score DECIMAL(5,2) NOT NULL DEFAULT 1.00 COMMENT '관계 신뢰도',
    is_current TINYINT(1) NOT NULL DEFAULT 1 COMMENT '현재 활성 관계 여부',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정일시',
    deleted_at DATETIME(6) NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_metadata_relation_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_metadata_relation_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    CONSTRAINT fk_metadata_relation_from_table
        FOREIGN KEY (from_table_id) REFERENCES metadata_table (id),
    CONSTRAINT fk_metadata_relation_from_column
        FOREIGN KEY (from_column_id) REFERENCES metadata_column (id),
    CONSTRAINT fk_metadata_relation_to_table
        FOREIGN KEY (to_table_id) REFERENCES metadata_table (id),
    CONSTRAINT fk_metadata_relation_to_column
        FOREIGN KEY (to_column_id) REFERENCES metadata_column (id),
    UNIQUE KEY uk_metadata_relation_current (
        project_id,
        from_table_id,
        from_column_id,
        to_table_id,
        to_column_id,
        is_current,
        is_deleted
    ),
    KEY idx_metadata_relation_project (project_id, is_current, is_deleted),
    KEY idx_metadata_relation_from (from_table_id, from_column_id),
    KEY idx_metadata_relation_to (to_table_id, to_column_id),
    CONSTRAINT ck_metadata_relation_soft_delete
        CHECK (
            (is_deleted = 0 AND deleted_at IS NULL)
            OR
            (is_deleted = 1 AND deleted_at IS NOT NULL)
        )
) ENGINE=InnoDB COMMENT='테이블/컬럼 관계 메타데이터';

CREATE TABLE metadata_import_history (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '적재 이력 PK',
    project_id BIGINT UNSIGNED NOT NULL COMMENT '프로젝트 PK',
    source_file_id BIGINT UNSIGNED NULL COMMENT '소스 파일 PK',
    import_status VARCHAR(30) NOT NULL COMMENT 'STARTED, SUCCEEDED, FAILED, ROLLED_BACK',
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '시작일시',
    finished_at DATETIME(6) NULL COMMENT '종료일시',
    error_message VARCHAR(4000) NULL COMMENT '오류 메시지',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_metadata_import_history_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_metadata_import_history_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    KEY idx_metadata_import_history_project (project_id, started_at),
    KEY idx_metadata_import_history_source_file (source_file_id)
) ENGINE=InnoDB COMMENT='업로드/적재 처리 이력';

-- ------------------------------------------------------------
-- Design notes
-- ------------------------------------------------------------
-- 1. 실제 업로드 문서는 information_schema export 형태이며,
--    헤더는 아래 항목을 기준으로 파싱된다.
--    테이블명 / 테이블 설명 / 순번 / 컬럼명 / 컬럼설명 / DATA TYPE /
--    데이터길이 / KEY / NULL값여부 / 자동순번 / 기본값
--
-- 2. 동일 project 내 동일 table_name 재업로드는 새 row를 추가하고,
--    기존 current row를 is_current = 0 으로 내려 최신본 우선 정책을 구현한다.
--
-- 3. soft delete는 deleted_at + is_deleted 조합으로 관리하며,
--    CHECK 제약으로 둘의 일관성을 강제한다.
--    MySQL 8 partial unique 제약이 없으므로 generated column(active_table_name)으로
--    현재 활성 테이블 유니크를 보장한다.
--
-- 3-1. schema_name을 추가해 동일 project 내 동명 table 충돌을 줄인다.
--      예약어를 사용하는 실제 물리 스키마/테이블/컬럼명은 애플리케이션에서
--      백틱(`)을 포함해 정규화된 식별자로 컨텍스트 조립 및 SQL 생성 시 반영한다.
--
-- 4. metadata_relation은 조회 시 재추론하지 않고 업로드 시 저장하는 방향이다.
--    추후 실제 DB 구조 변경 동기화가 들어오면 inference_source = DB_SYNC 또는 MANUAL로 확장한다.
--
-- 5. 브라우저 종료 시 세션 제거 정책은 DB 스키마 이슈가 아니라 애플리케이션 세션 정책이다.
--    따라서 init.sql에는 별도 session table을 두지 않았다.
