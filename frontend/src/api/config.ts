/**
 * 배포 환경별 API origin 분리 지점. 미설정(기본값) 시 상대경로를 사용하며,
 * 이는 현재 배포 모델(React 빌드 산출물을 Spring Boot가 같은 오리진에서 정적 서빙)과 일치한다.
 * 프론트엔드를 백엔드와 분리 배포해야 하는 환경이 생기면 .env.[mode]에
 * VITE_API_BASE_URL만 채우면 되고, 각 api/*.ts의 경로 상수는 변경할 필요가 없다.
 */
export const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''
