/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 배포 환경별 API origin. 미설정 시 같은 오리진 상대경로 사용 (api/config.ts 참고). */
  readonly VITE_API_BASE_URL?: string
}
