-- ============================================================
-- V3: 프로젝트 업무 규칙 (이슈 #24)
-- 테이블/컬럼은 ID 가 아닌 이름으로 가리킨다. 정의서 재업로드 때 metadata_table 행이 새 ID 로
-- 다시 만들어지기 때문이다. 대상 존재 여부(targetStatus)는 저장하지 않고 조회 시 계산한다.
-- rule_type 허용 값은 애플리케이션 enum 으로 검증하며 CHECK 제약은 두지 않는다 (V2 와 같은 방침).
-- condition_text 는 프롬프트에만 쓰며 실행하지 않는다.
-- ============================================================
CREATE TABLE project_business_rule (
    id                  BIGINT        NOT NULL AUTO_INCREMENT COMMENT '규칙 PK',
    project_id          BIGINT        NOT NULL COMMENT 'metadata_project.id FK',
    rule_type           VARCHAR(30)   NOT NULL COMMENT 'TERM | TABLE_DEFAULT_FILTER | PERIOD_COLUMN | JOIN_WITH | COLUMN_VALUES',
    term                VARCHAR(100)      NULL COMMENT '용어 (TERM)',
    term_aliases        VARCHAR(300)      NULL COMMENT '용어 별칭, 쉼표 구분 (TERM, 선택)',
    table_name          VARCHAR(255)  NOT NULL COMMENT '대상 테이블명 (metadata_table.table_name 과 이름으로 비교)',
    column_name         VARCHAR(255)      NULL COMMENT '대상 컬럼명',
    related_table_name  VARCHAR(255)      NULL COMMENT '함께 쓰는 테이블명 (JOIN_WITH)',
    condition_text      VARCHAR(1000)     NULL COMMENT '조건식. 프롬프트에만 쓰며 실행하지 않음',
    value_items_json    VARCHAR(4000)     NULL COMMENT '값 목록 JSON 배열 [{"code":"..","label":".."}]',
    auto_include        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'JOIN_WITH: Step1 결과에 자동 포함',
    description         VARCHAR(1000)     NULL COMMENT '보조 설명 (자유 텍스트)',
    is_enabled          TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '사용 여부',
    is_deleted          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '소프트 삭제 여부 (0=정상, 1=삭제)',
    created_at          DATETIME(6)   NOT NULL COMMENT '생성일시',
    updated_at          DATETIME(6)   NOT NULL COMMENT '수정일시',
    deleted_at          DATETIME(6)       NULL COMMENT '삭제일시 (is_deleted=1 일 때만 값 존재)',
    PRIMARY KEY (id),
    CONSTRAINT fk_business_rule_project FOREIGN KEY (project_id) REFERENCES metadata_project (id),
    INDEX idx_business_rule_project (project_id, is_deleted, is_enabled),
    CONSTRAINT ck_business_rule_soft_delete CHECK (
        (is_deleted = 0 AND deleted_at IS NULL)
        OR (is_deleted = 1 AND deleted_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='프로젝트 업무 규칙';
