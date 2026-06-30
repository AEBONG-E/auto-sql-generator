-- ============================================================
-- V1__init_metadata_foundation.sql
-- Atlas Metadata Foundation — 초기 스키마 생성 (Flyway V1)
--
-- [설계 결정 요약]
-- 1. RDBMS: MySQL 8.0+
-- 2. 삭제 정책: Soft Delete (is_deleted + deleted_at)
-- 3. Relation 저장: 업로드 시 즉시 저장 (조회 시 재추론 없음)
-- 4. Migration 도구: Flyway
--
-- [JPA 호환성 주의사항]
-- - PK/FK 모두 BIGINT(signed) 사용 — JPA Long 타입과 일치
--   (init.sql의 BIGINT UNSIGNED 는 JPA Entity와 타입 불일치 발생)
-- - active_table_name: STORED Generated Column, insertable=false updatable=false 설정과 대응
-- - ddl-auto는 반드시 none 또는 validate 로 설정 (Flyway 가 DDL 단독 관리)
-- ============================================================

-- ------------------------------------------------------------
-- 1. metadata_project — 프로젝트 (최상위 소유 단위)
-- ------------------------------------------------------------
CREATE TABLE metadata_project (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '프로젝트 PK',
    project_key     VARCHAR(100) NOT NULL COMMENT '프로젝트 식별 키 (unique)',
    project_name    VARCHAR(200) NOT NULL COMMENT '프로젝트 명',
    project_status  VARCHAR(30)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE | INACTIVE | ARCHIVED',
    description     VARCHAR(1000)    NULL COMMENT '프로젝트 설명',
    is_deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부 (0=정상, 1=삭제)',
    created_at      DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at      DATETIME(6)  NOT NULL COMMENT '수정일시',
    deleted_at      DATETIME(6)      NULL COMMENT '삭제일시 (is_deleted=1 일 때만 값 존재)',
    PRIMARY KEY (id),
    CONSTRAINT uk_metadata_project_project_key UNIQUE (project_key),
    INDEX idx_metadata_project_status (project_status, deleted_at),
    CONSTRAINT ck_metadata_project_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='메타데이터 프로젝트';

-- ------------------------------------------------------------
-- 2. metadata_source_file — 업로드 소스 파일
-- ------------------------------------------------------------
CREATE TABLE metadata_source_file (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '소스 파일 PK',
    project_id          BIGINT       NOT NULL COMMENT 'metadata_project.id FK',
    original_filename   VARCHAR(255) NOT NULL COMMENT '업로드 원본 파일명',
    stored_filename     VARCHAR(255)     NULL COMMENT '내부 저장 파일명 또는 오브젝트 키',
    upload_checksum     CHAR(64)         NULL COMMENT 'SHA-256 체크섬 (중복 업로드 감지용)',
    source_type         VARCHAR(30)  NOT NULL DEFAULT 'XLSX' COMMENT 'XLSX | DB_SYNC | MANUAL',
    import_status       VARCHAR(30)  NOT NULL DEFAULT 'IMPORTED' COMMENT 'IMPORTED | FAILED | ROLLED_BACK',
    header_signature    VARCHAR(500)     NULL COMMENT '헤더 구조 식별 문자열',
    table_count         INT          NOT NULL DEFAULT 0 COMMENT '파일 내 테이블 수',
    column_count        INT          NOT NULL DEFAULT 0 COMMENT '파일 내 컬럼 수',
    relation_count      INT          NOT NULL DEFAULT 0 COMMENT '파일 내 추론된 관계 수',
    uploaded_at         DATETIME(6)  NOT NULL COMMENT '업로드 시각',
    is_deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at          DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at          DATETIME(6)  NOT NULL COMMENT '수정일시',
    deleted_at          DATETIME(6)      NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_source_file_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    INDEX idx_metadata_source_file_project     (project_id, deleted_at),
    INDEX idx_metadata_source_file_uploaded_at (uploaded_at),
    INDEX idx_metadata_source_file_checksum    (upload_checksum),
    CONSTRAINT ck_metadata_source_file_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='업로드 원본 파일 메타데이터';

-- ------------------------------------------------------------
-- 3. metadata_table — 테이블 메타데이터 (버전 관리)
--
-- [active_table_name Generated Column 설계 의도]
-- MySQL은 Partial Unique Index (WHERE 절) 를 지원하지 않으므로,
-- GENERATED ALWAYS AS STORED 컬럼을 활용하여 활성 행(is_current=1, is_deleted=0)에만
-- unique 제약을 적용한다.
-- 비활성 행(is_current=0 또는 is_deleted=1)은 NULL을 반환하므로 unique 제약 대상에서 제외된다.
-- (MySQL에서 NULL은 UNIQUE INDEX 중복 대상이 아님)
--
-- [JPA Entity와의 매핑]
-- @Column(name="active_table_name", insertable=false, updatable=false)
-- JPA는 이 컬럼에 값을 쓰지 않으며, MySQL이 자동으로 계산하여 저장함.
-- 단, ddl-auto=validate 시 Hibernate의 Generated Column 타입 검사에 주의 필요.
-- 안전을 위해 ddl-auto=none 권장.
--
-- [CONCAT 길이 주의]
-- init.sql(초안)은 CONCAT(schema_name, '.', table_name)으로 스키마 접두사를 포함했으나,
-- JPA Entity의 active_table_name 컬럼 길이가 VARCHAR(255)로 선언되어
-- schema_name(255) + '.' + table_name(255) = 최대 511자 → 오버플로우 위험.
-- 따라서 본 스크립트는 table_name만 사용하며,
-- project_id + active_table_name 조합으로 프로젝트 내 유일성을 보장한다.
-- (같은 프로젝트 내 동명 테이블이 다른 schema_name을 가질 경우,
--  현재 v0.0.1 요구사항 범위에서는 최신 업로드 우선 정책으로 처리한다.)
-- ------------------------------------------------------------
CREATE TABLE metadata_table (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '테이블 메타 PK',
    project_id          BIGINT       NOT NULL COMMENT 'metadata_project.id FK',
    source_file_id      BIGINT       NOT NULL COMMENT 'metadata_source_file.id FK',
    schema_name         VARCHAR(255) NOT NULL DEFAULT 'default' COMMENT '논리 스키마명',
    table_name          VARCHAR(255) NOT NULL COMMENT '물리 테이블명',
    table_description   VARCHAR(1000)    NULL COMMENT '테이블 설명',
    current_version_no  INT          NOT NULL DEFAULT 1 COMMENT '동일 table_name 기준 버전 번호',
    is_current          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '최신 활성 버전 여부 (1=현재, 0=구버전)',
    is_deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    active_table_name   VARCHAR(255) GENERATED ALWAYS AS (
                            CASE
                                WHEN is_deleted = 0 AND is_current = 1 THEN table_name
                                ELSE NULL
                            END
                        ) STORED COMMENT '활성 테이블 유니크 보장용 Generated Column (NULL이면 unique 대상 아님)',
    created_at          DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at          DATETIME(6)  NOT NULL COMMENT '수정일시',
    deleted_at          DATETIME(6)      NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_table_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_table_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    CONSTRAINT uk_metadata_table_current UNIQUE (project_id, active_table_name),
    INDEX idx_metadata_table_lookup      (project_id, is_current, is_deleted),
    INDEX idx_metadata_table_source_file (source_file_id, is_deleted),
    CONSTRAINT ck_metadata_table_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='테이블 메타데이터 (버전 관리 포함)';

-- ------------------------------------------------------------
-- 4. metadata_column — 컬럼 메타데이터
-- ------------------------------------------------------------
CREATE TABLE metadata_column (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '컬럼 메타 PK',
    table_id            BIGINT       NOT NULL COMMENT 'metadata_table.id FK',
    ordinal_position    INT          NOT NULL COMMENT '컬럼 순서 (1-based)',
    column_name         VARCHAR(255) NOT NULL COMMENT '컬럼명',
    column_comment      VARCHAR(1000)    NULL COMMENT '컬럼 설명',
    data_type           VARCHAR(100) NOT NULL COMMENT 'DATA TYPE (예: VARCHAR, INT, DATETIME)',
    column_type         VARCHAR(255)     NULL COMMENT 'COLUMN TYPE 원문 (예: VARCHAR(100))',
    key_type            VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT 'PRI | UNI | MUL | NONE',
    nullable_yn         CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT 'Y=NULL 허용, N=NOT NULL',
    auto_increment_yn   CHAR(1)      NOT NULL DEFAULT 'N' COMMENT 'Y=자동 증가, N=일반',
    default_value       VARCHAR(1000)    NULL COMMENT '기본값',
    is_deleted          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at          DATETIME(6)  NOT NULL COMMENT '생성일시',
    updated_at          DATETIME(6)  NOT NULL COMMENT '수정일시',
    deleted_at          DATETIME(6)      NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_column_table
        FOREIGN KEY (table_id) REFERENCES metadata_table (id),
    CONSTRAINT uk_metadata_column_table_name UNIQUE (table_id, column_name),
    INDEX idx_metadata_column_table_ordinal (table_id, ordinal_position),
    INDEX idx_metadata_column_name          (column_name),
    CONSTRAINT ck_metadata_column_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='컬럼 메타데이터';

-- ------------------------------------------------------------
-- 5. metadata_relation — 테이블/컬럼 관계 메타데이터
--
-- [uk_metadata_relation_current 설계 결정]
-- 기존 JPA Entity 및 init.sql 초안은 unique key에 is_deleted를 포함했으나
-- 이는 동일 관계의 3차 이상 재업로드 시 archived 행(is_current=0, is_deleted=0)
-- 충돌 위험을 내포한다.
--
-- 수정 방향: unique key를 (project_id, from_table_id, from_column_id,
--            to_table_id, to_column_id, is_current) 5+1 컬럼으로 제한.
-- - is_current=1인 동일 관계가 두 개 존재하는 것만 차단 (실질 중복 방지)
-- - soft delete(is_deleted=1)된 행은 unique 대상에서 제외되지 않으므로,
--   재임포트 시 기존 행을 is_current=0으로 내린 후 신규 행(is_current=1)을 삽입해야 함
-- - 삭제 처리 순서: UPDATE is_current=0 → INSERT new row → (optional) soft delete old rows
--
-- [Entity 수정 필요사항]
-- MetadataRelationEntity의 @UniqueConstraint columnNames에서 "is_deleted" 제거 필요.
-- (현재 Entity 설계와 본 DDL이 불일치 → Hibernate ddl-auto=validate 시 경고 발생 가능)
-- ------------------------------------------------------------
CREATE TABLE metadata_relation (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '관계 메타 PK',
    project_id      BIGINT          NOT NULL COMMENT 'metadata_project.id FK',
    source_file_id  BIGINT          NOT NULL COMMENT '관계 생성 소스 파일 FK',
    from_table_id   BIGINT          NOT NULL COMMENT '출발 테이블 FK',
    from_column_id  BIGINT          NOT NULL COMMENT '출발 컬럼 FK',
    to_table_id     BIGINT          NOT NULL COMMENT '도착 테이블 FK',
    to_column_id    BIGINT          NOT NULL COMMENT '도착 컬럼 FK',
    relation_type   VARCHAR(30)     NOT NULL COMMENT 'ONE_TO_MANY | MANY_TO_MANY | SELF_REFERENCE',
    inference_source VARCHAR(30)    NOT NULL DEFAULT 'HEURISTIC' COMMENT 'HEURISTIC | MANUAL | DB_SYNC',
    confidence_score DECIMAL(5,2)   NOT NULL DEFAULT 1.00 COMMENT '관계 신뢰도 (0.00~1.00)',
    is_current      TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '현재 활성 관계 여부',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부',
    created_at      DATETIME(6)     NOT NULL COMMENT '생성일시',
    updated_at      DATETIME(6)     NOT NULL COMMENT '수정일시',
    deleted_at      DATETIME(6)         NULL COMMENT '삭제일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_relation_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_relation_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    CONSTRAINT fk_relation_from_table
        FOREIGN KEY (from_table_id) REFERENCES metadata_table (id),
    CONSTRAINT fk_relation_from_column
        FOREIGN KEY (from_column_id) REFERENCES metadata_column (id),
    CONSTRAINT fk_relation_to_table
        FOREIGN KEY (to_table_id) REFERENCES metadata_table (id),
    CONSTRAINT fk_relation_to_column
        FOREIGN KEY (to_column_id) REFERENCES metadata_column (id),
    -- is_deleted를 제외한 5+1 컬럼 unique (init.sql 초안의 7컬럼에서 수정됨)
    CONSTRAINT uk_metadata_relation_current UNIQUE (
        project_id, from_table_id, from_column_id,
        to_table_id, to_column_id, is_current
    ),
    INDEX idx_metadata_relation_project (project_id, is_current, is_deleted),
    INDEX idx_metadata_relation_from    (from_table_id, from_column_id),
    INDEX idx_metadata_relation_to      (to_table_id, to_column_id),
    CONSTRAINT ck_metadata_relation_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='테이블/컬럼 관계 메타데이터 (업로드 시 저장, 재추론 없음)';

-- ------------------------------------------------------------
-- 6. metadata_import_history — 업로드/적재 처리 이력
--
-- [설계 의도]
-- 이력 테이블은 감사(audit) 목적이므로 soft delete 미적용.
-- SoftDeletableAuditEntity를 상속하지 않는 MetadataImportHistoryEntity와 일치.
-- ------------------------------------------------------------
CREATE TABLE metadata_import_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '적재 이력 PK',
    project_id      BIGINT       NOT NULL COMMENT 'metadata_project.id FK',
    source_file_id  BIGINT           NULL COMMENT 'metadata_source_file.id FK (실패 시 NULL 가능)',
    import_status   VARCHAR(30)  NOT NULL COMMENT 'STARTED | SUCCEEDED | FAILED | ROLLED_BACK',
    started_at      DATETIME(6)  NOT NULL COMMENT '시작일시',
    finished_at     DATETIME(6)      NULL COMMENT '종료일시',
    error_message   VARCHAR(4000)    NULL COMMENT '오류 메시지 (실패 시)',
    created_at      DATETIME(6)  NOT NULL COMMENT '생성일시',
    PRIMARY KEY (id),
    CONSTRAINT fk_import_history_project
        FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    CONSTRAINT fk_import_history_source_file
        FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    INDEX idx_metadata_import_history_project     (project_id, started_at),
    INDEX idx_metadata_import_history_source_file (source_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='업로드/적재 처리 이력 (audit 테이블, soft delete 미적용)';
