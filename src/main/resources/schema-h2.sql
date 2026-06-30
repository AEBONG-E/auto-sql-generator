-- H2 In-Memory Schema (MySQL Flyway V1 호환 버전)
-- MySQL GENERATED ALWAYS AS STORED → H2 computed column 문법 적용

DROP TABLE IF EXISTS metadata_import_history;
DROP TABLE IF EXISTS metadata_relation;
DROP TABLE IF EXISTS metadata_column;
DROP TABLE IF EXISTS metadata_table;
DROP TABLE IF EXISTS metadata_source_file;
DROP TABLE IF EXISTS metadata_project;

CREATE TABLE metadata_project (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    project_key     VARCHAR(100)    NOT NULL,
    project_name    VARCHAR(200)    NOT NULL,
    project_status  VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
    description     VARCHAR(1000),
    is_deleted      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL,
    updated_at      TIMESTAMP       NOT NULL,
    deleted_at      TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_metadata_project_project_key UNIQUE (project_key)
);

CREATE TABLE metadata_source_file (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    project_id          BIGINT          NOT NULL,
    original_filename   VARCHAR(255)    NOT NULL,
    stored_filename     VARCHAR(255),
    upload_checksum     VARCHAR(64),
    source_type         VARCHAR(30)     NOT NULL DEFAULT 'XLSX',
    import_status       VARCHAR(30)     NOT NULL DEFAULT 'IMPORTED',
    header_signature    VARCHAR(500),
    table_count         INT             NOT NULL DEFAULT 0,
    column_count        INT             NOT NULL DEFAULT 0,
    relation_count      INT             NOT NULL DEFAULT 0,
    uploaded_at         TIMESTAMP       NOT NULL,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    deleted_at          TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_source_file_project FOREIGN KEY (project_id) REFERENCES metadata_project (id)
);

-- active_table_name: H2에서는 일반 컬럼으로 선언 (MySQL 운영 환경에서는 GENERATED ALWAYS AS STORED 적용)
-- H2에서는 서비스 레이어의 existsCurrentTable 체크로 중복 방지
CREATE TABLE metadata_table (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    project_id          BIGINT          NOT NULL,
    source_file_id      BIGINT          NOT NULL,
    schema_name         VARCHAR(255)    NOT NULL DEFAULT 'default',
    table_name          VARCHAR(255)    NOT NULL,
    table_description   VARCHAR(1000),
    current_version_no  INT             NOT NULL DEFAULT 1,
    is_current          BOOLEAN         NOT NULL DEFAULT TRUE,
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    active_table_name   VARCHAR(255),
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    deleted_at          TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_table_project     FOREIGN KEY (project_id)     REFERENCES metadata_project (id),
    CONSTRAINT fk_table_source_file FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id)
);

CREATE TABLE metadata_column (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    table_id            BIGINT          NOT NULL,
    ordinal_position    INT             NOT NULL,
    column_name         VARCHAR(255)    NOT NULL,
    column_comment      VARCHAR(1000),
    data_type           VARCHAR(100)    NOT NULL,
    column_type         VARCHAR(255),
    key_type            VARCHAR(20)     NOT NULL DEFAULT 'NONE',
    nullable_yn         CHAR(1)         NOT NULL DEFAULT 'Y',
    auto_increment_yn   CHAR(1)         NOT NULL DEFAULT 'N',
    default_value       VARCHAR(1000),
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    deleted_at          TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_column_table FOREIGN KEY (table_id) REFERENCES metadata_table (id),
    CONSTRAINT uk_metadata_column_table_name UNIQUE (table_id, column_name)
);

CREATE TABLE metadata_relation (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    project_id       BIGINT          NOT NULL,
    source_file_id   BIGINT          NOT NULL,
    from_table_id    BIGINT          NOT NULL,
    from_column_id   BIGINT          NOT NULL,
    to_table_id      BIGINT          NOT NULL,
    to_column_id     BIGINT          NOT NULL,
    relation_type    VARCHAR(30)     NOT NULL,
    inference_source VARCHAR(30)     NOT NULL DEFAULT 'HEURISTIC',
    confidence_score DECIMAL(5,2)    NOT NULL DEFAULT 1.00,
    is_current       BOOLEAN         NOT NULL DEFAULT TRUE,
    is_deleted       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP       NOT NULL,
    updated_at       TIMESTAMP       NOT NULL,
    deleted_at       TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_relation_project     FOREIGN KEY (project_id)    REFERENCES metadata_project (id),
    CONSTRAINT fk_relation_source_file FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id),
    CONSTRAINT fk_relation_from_table  FOREIGN KEY (from_table_id) REFERENCES metadata_table (id),
    CONSTRAINT fk_relation_from_column FOREIGN KEY (from_column_id) REFERENCES metadata_column (id),
    CONSTRAINT fk_relation_to_table    FOREIGN KEY (to_table_id)   REFERENCES metadata_table (id),
    CONSTRAINT fk_relation_to_column   FOREIGN KEY (to_column_id)  REFERENCES metadata_column (id)
);

CREATE TABLE metadata_import_history (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    project_id      BIGINT          NOT NULL,
    source_file_id  BIGINT,
    import_status   VARCHAR(30)     NOT NULL,
    started_at      TIMESTAMP       NOT NULL,
    finished_at     TIMESTAMP,
    error_message   VARCHAR(4000),
    created_at      TIMESTAMP       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_import_history_project     FOREIGN KEY (project_id)    REFERENCES metadata_project (id),
    CONSTRAINT fk_import_history_source_file FOREIGN KEY (source_file_id) REFERENCES metadata_source_file (id)
);
