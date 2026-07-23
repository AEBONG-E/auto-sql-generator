# Auto-SQL Generator (Atlas Metadata Foundation)

엑셀 테이블 정의서를 업로드해 메타데이터로 영속화하고, 이를 기반으로 **ERD 시각화**와 **로컬 LLM(Ollama) 기반 자연어 → SQL 생성**을 제공하는 메타데이터 워크벤치입니다.

> 이번 단계(v0.0.1)의 중심은 세션 메모리 기반 저장을 `프로젝트 > 소스 파일 > 테이블 > 컬럼 > 관계` 구조의 RDBMS 메타데이터로 전환하고, ERD/SQL 생성의 source of truth를 DB로 옮기는 것입니다.

---

## 핵심 기능

- **메타데이터 적재**: `.xlsx` 테이블 정의서 업로드 → 파싱 → DB 정규화 저장 (한/영 헤더 alias 지원, 다중 파일)
- **ERD 조회**: 프로젝트 기준 테이블/컬럼/관계 조회, draw.io 임베드 시각화, 테이블 필터
- **자연어 SQL 생성**: 2단계 LLM 구조(① 관련 테이블 선별 → ② SQL 생성)로 자연어 질의를 SQL 초안으로 변환, SSE 스트리밍 출력
- **프로젝트 관리**: 생성/목록/조회, 파일 삭제, 프로젝트 초기화

## 기술 스택

| 영역 | 스택 |
|---|---|
| Backend | Spring Boot 3.5.0, Java 17, Gradle |
| Persistence | Spring Data JPA, Flyway, MySQL 8.0+(prod) / H2(local·test) |
| LLM | Ollama + gemma4 (2단계 생성, SSE 스트리밍) |
| Frontend | React 19 + Vite + TypeScript (다크모드), draw.io 임베드 |

## 아키텍처

포트/어댑터 + 유스케이스 + read model 분리 구조입니다.

```
com.autoerd
  .web              # API 입출력 계약 (ProjectController, MetadataImportController 등)
  .application      # 유스케이스 orchestration (Import/GetErd/Delete/Reset/GenerateSql)
  .domain           # 메타데이터/SQL 도메인 모델·정책 (RelationInferencePolicy 등)
  .infrastructure   # JPA, Excel(POI), Ollama 어댑터
  .readmodel/dto    # ERD 응답·SQL 컨텍스트 조립(assembler)
```

- **JPA Entity = Domain = DTO 삼중 겸용 금지**, LLM 호출은 DB 트랜잭션 밖
- 최신 업로드 우선(`MetadataConflictPolicy`), `_id` 휴리스틱 관계 추론(`RelationInferencePolicy`)

## 실행 방법

### 사전 요구

- JDK 17
- Ollama 및 `gemma4` 모델 (`ollama pull gemma4`) — SQL 생성 기능 사용 시
- (prod) MySQL 8.0+

### 로컬 실행 (H2 인메모리)

```bash
# Ollama 기동 (SQL 생성용)
ollama serve

# 앱 실행 (기본 local 프로파일, H2)
./gradlew bootRun
# → http://localhost:8880
```

빌드 시 `npmBuild`가 선행되어 React 산출물이 `src/main/resources/static`으로 출력되고 Spring Boot 정적 리소스로 서빙됩니다.

### 운영 실행 (MySQL + Flyway)

```bash
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL="jdbc:mysql://<host>:3306/atlas_metadata" \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<pass> \
./gradlew bootRun
```

- `prod`는 Flyway가 `db/migration/V1__init_metadata_foundation.sql`로 스키마를 단독 관리합니다(`ddl-auto: none`).

## 주요 API

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/projects` | 프로젝트 생성 |
| GET | `/api/v1/projects` | 프로젝트 목록 |
| POST | `/api/v1/projects/{id}/schemas:import` | 엑셀 업로드 |
| GET | `/api/v1/projects/{id}/files` | 파일 목록 |
| DELETE | `/api/v1/projects/{id}/files/{fileId}` | 파일 삭제 |
| POST | `/api/v1/projects/{id}/reset` | 프로젝트 초기화 |
| GET | `/api/v1/projects/{id}/erd` | ERD 조회 |
| POST | `/api/v1/projects/{id}/sql/generate` | SQL 생성 (SSE) |

## 테스트

```bash
./gradlew test
```

슬라이스(@DataJpaTest)·통합(@SpringBootTest)·정책·파싱·회귀 테스트를 포함합니다.

## 문서

요구사항/설계 문서(PRD, Design Walkthrough 등)는 `requirements-analysis/` 디렉터리에서 관리합니다. 이 디렉터리는 로컬 전용으로 형상관리에서 제외됩니다.
