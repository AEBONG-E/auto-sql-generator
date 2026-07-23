# auto-sql-generator Frontend

React + TypeScript(Vite) 프론트엔드. `docs/design/ui-ux-spec-v0.0.1.md` 명세를 기반으로 구현되었다.

## 개발

```bash
npm install
npm run dev   # http://localhost:5173, /api는 8880으로 프록시
```

백엔드는 별도로 `local` 프로파일로 8880 포트에서 실행되어야 한다.

## 빌드

```bash
npm run build
```

`vite.config.ts`의 `build.outDir` 설정에 따라 산출물이 `../src/main/resources/static`에 직접 생성되며,
Spring Boot가 정적 리소스로 서빙한다. `./gradlew build`를 실행하면 `npmBuild` 태스크가 자동으로 먼저 실행된다.
