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

### 0) Docker로 상태 DB(Postgres) 실행

```bash
docker compose up -d
docker compose ps
```

종료:

```bash
docker compose down
```

### 1) DB 준비 (로컬 Postgres)

환경변수 미지정 시 기본값:

- `DB_URL=jdbc:postgresql://localhost:5432/minicloud`
- `DB_USERNAME=minicloud`
- `DB_PASSWORD=minicloud`

### 2) 서버 실행

```bash
cd server
../gradlew bootRun
```

참고: 서버는 `kubectl`로 클러스터를 직접 제어하므로, 현재는 서버 컨테이너화보다 호스트에서 실행하는 구성이 안전합니다.

`deploy/docker/server.env.example`를 기반으로 환경변수를 주고 실행하려면:

```bash
set -a
source ../deploy/docker/server.env.example
set +a
../gradlew bootRun
```

### 3) Database 생성

```bash
curl -X POST http://localhost:8080/v1/databases \
  -H "Content-Type: application/json" \
  -d '{"name":"pg-main","namespace":"demo"}'
```

Database API를 호출하면 현재 구현은 실제 `kubectl`로 다음을 생성합니다.

- Namespace (`demo`)
- Secret (`pg-main-conn`)
- Pod (`pg-main-pg`, image: `postgres:16-alpine`)
- Service (`pg-main-svc`)

확인:

```bash
kubectl -n demo get pod,svc,secret | grep pg-main
kubectl -n demo logs pg-main-pg
```

`kubectl` 또는 클러스터가 준비되지 않은 상태에서 `POST /v1/databases`를 호출하면 `503`과 함께
`kubectl이 실행/연결되어 있지 않습니다. 먼저 minikube와 kubectl을 실행해 주세요.` 메시지를 반환합니다.

### E2E 테스트 (실제 Pod 생성 검증)

아래 테스트는 실제 엔드포인트를 호출하고 Kubernetes에 Postgres Pod가 생성되는지 검증합니다.

- 테스트 파일: `server/src/test/kotlin/io/minicloud/controlplane/e2e/DatabasePodProvisionE2ETest.kt`
- 실행 조건: `RUN_DB_POD_E2E=true`

```bash
export RUN_DB_POD_E2E=true
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :server:test --tests '*DatabasePodProvisionE2ETest'
```

App + DB 연동 E2E:

```bash
export RUN_APP_DB_E2E=true
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :server:test --tests '*AppWithDatabaseE2ETest'
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

`databaseRef`를 지정하면 App Deployment에 아래 환경변수가 자동 주입됩니다.

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`

생성 리소스:

- Deployment (`<app-name>`)
- Service (`<app-name>-svc`)
- Ingress (`<app-name>-ing`)

Kubernetes 매니페스트 템플릿 위치:

- `server/src/main/resources/k8s/templates/database.yaml`
- `server/src/main/resources/k8s/templates/app.yaml`
- `server/src/main/resources/k8s/templates/app-db-env.yaml`

## Web Console (모노레포 `web` 앱)

React + Vite 기반 웹 콘솔이 서버에 내장되어 제공됩니다.

### 사용자 접근 (npm 불필요)

서버만 실행하면 됩니다.

```bash
cd server
../gradlew bootRun
```

접속:

- `http://localhost:8080/console`

### 개발자용: web 앱 단독 실행

```bash
cd web
npm install
npm run dev
```

단독 실행 시 API URL은 아래로 조정:

```bash
cp .env.example .env
# .env -> VITE_API_BASE_URL 수정
```

서버는 기본적으로 아래 오리진에서의 API 호출(CORS)을 허용합니다.

- `http://localhost:5173`
- `http://127.0.0.1:5173`
- `http://localhost:5500`
- `http://127.0.0.1:5500`

필요하면 환경변수로 조정:

```bash
export MINICLOUD_WEB_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:3000
```

참고:

- Gradle이 서버 리소스 처리 시 `web`을 자동 빌드해 `/console`로 포함합니다.
- 웹 빌드를 건너뛰려면 `MINICLOUD_SKIP_WEB_BUILD=true`를 사용할 수 있습니다.
