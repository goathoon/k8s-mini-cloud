# k8s-mini-cloud

API 기반 로컬 Kubernetes Internal Developer Platform(IDP) 실험 프로젝트입니다.

`mini-cloud`는 Kubernetes 리소스를 직접 작성하지 않고, Control Plane API 호출만으로 App/DB를 프로비저닝하는 것을 목표로 합니다.

## Architecture (MVP)

- Developer -> mini-cloud API (Spring Boot)
- mini-cloud API -> 상태 DB(Postgres)
- mini-cloud API -> Kubernetes API/Helm (오케스트레이션)

## 현재 포함된 코드 뼈대

- Spring Boot 3 + Kotlin 기반 `server` 모듈
- 상태 머신 enum (`REQUESTED -> PROVISIONING -> READY/FAILED -> DELETING -> DELETED`)
- MVP API:
  - `POST /v1/databases`
  - `POST /v1/apps`
  - `GET /v1/apps/{name}?namespace=...`
  - `DELETE /v1/apps/{name}?namespace=...`
- JPA 기반 상태 저장 엔티티/리포지토리
- 오케스트레이션 인터페이스 + Helm/K8s 스텁 구현

## Quick Start

### 1) DB 준비 (로컬 Postgres)

환경변수 미지정 시 기본값:

- `DB_URL=jdbc:postgresql://localhost:5432/minicloud`
- `DB_USERNAME=minicloud`
- `DB_PASSWORD=minicloud`

### 2) 서버 실행

```bash
cd server
gradle bootRun
```

### 3) Database 생성

```bash
curl -X POST http://localhost:8080/v1/databases \
  -H "Content-Type: application/json" \
  -d '{"name":"pg-main","namespace":"demo"}'
```

### 4) App 생성

```bash
curl -X POST http://localhost:8080/v1/apps \
  -H "Content-Type: application/json" \
  -d '{
    "name":"hello",
    "namespace":"demo",
    "image":"nginx:latest",
    "port":80,
    "replicas":1,
    "databaseRef":"pg-main"
  }'
```
