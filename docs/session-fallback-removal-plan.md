# SchemaSessionStore(레거시 세션 fallback) 축소/제거 계획

## 현재 사용처 (v0.0.1 기준)

`SchemaSessionStore`(`@SessionScope`, 인메모리·비영속)는 DB 기반 메타데이터 파이프라인(Stage 3, `#3 Backend`) 도입 이전의
경로에서만 사용된다.

| 레거시 경로 (세션 기반, fallback) | 대체된 v1 경로 (DB 기반) |
| --- | --- |
| `ErdController` (`/api/v1/erd/generate`, `/schema`, `/reset`) → `SchemaSessionStore` | `MetadataImportController` + `ProjectErdQueryController` (`/api/v1/projects/{projectId}/schemas:import`, `/erd`) |
| `SqlGenerationController` (`/api/v1/sql/generate`) → `OllamaSqlService` → `SchemaSessionStore` | `ProjectSqlGenerationController` (`/api/v1/projects/{projectId}/sql/generate`) → `GenerateProjectSqlService` → `JpaSqlMetadataProviderAdapter`(DB 조회) |

프론트엔드(`frontend/src/api/erd.ts`, `frontend/src/api/sql.ts`, `frontend/src/api/files.ts`)는 이미 **전량
`/api/v1/projects/{projectId}/...` DB 기반 경로만 호출**한다. 레거시 `/api/v1/erd/*`, `/api/v1/sql/generate`는
프론트엔드에서 더 이상 호출되지 않으며, `AutoErdGeneratorApplicationTests`의 PNA(Private Network Access) preflight
회귀 테스트가 `/api/v1/erd/generate` 경로에 대해 CORS 헤더 계약만 검증하기 위해 참조하고 있다.

## v0.0.1에서 유지하는 범위

- 레거시 컨트롤러/서비스(`ErdController`, `SqlGenerationController`, `OllamaSqlService`, `SchemaSessionStore`) 코드는
  **삭제하지 않는다.** 외부 연동(예: 북마크된 구 API 호출, 수동 QA 스크립트)이 남아 있을 가능성을 배제할 수 없어
  즉시 제거는 회귀 위험이 있다.
- 신규 기능은 전량 DB 기반 v1 경로에만 추가한다. 레거시 경로에 신규 기능을 얹지 않는다.

## 제거 조건 / 시점 (다음 단계 계획)

아래 조건이 **모두** 충족되면 레거시 경로 제거를 별도 이슈로 진행한다:

1. 프론트엔드가 레거시 경로(`/api/v1/erd/*`, `/api/v1/sql/generate`)를 더 이상 참조하지 않음을 코드 검색으로
   재확인(현재도 미참조 상태이나, 제거 시점에 재검증 필요).
2. `AutoErdGeneratorApplicationTests`의 PNA preflight 회귀 테스트를 v1 경로(`/api/v1/projects/{projectId}/erd`
   또는 `/schemas:import`) 기준으로 이관.
3. 운영 환경 액세스 로그(또는 APM)에서 레거시 경로 호출이 일정 기간(예: 1~2 릴리스 주기) 0건임을 확인.
4. 위 조건 충족 후에만 `ErdController`, `SqlGenerationController`, `OllamaSqlService`, `SchemaSessionStore`와
   관련 프론트엔드 잔여 코드(있다면)를 함께 제거하는 별도 마이그레이션 이슈를 생성한다.

이번 이슈(#7 Stage 4) 범위는 위 계획의 **문서화까지**이며, 실제 코드 제거는 포함하지 않는다.
