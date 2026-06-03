# CI/CD Workflow Overview

> 문서 성격: 보조 참고 문서(Task Reference)
>
> active canonical 문서와 충돌하면 active canonical 문서가 우선한다.

## CI 범위

`.github/workflows/ci.yaml`은 pull request, `develop`/`main` push, 수동 실행에서 빠른 mock/dev validation을 담당한다.

검증 job:

- `backend-test`: Java 21과 Gradle CLI로 Spring Boot test를 실행한다.
- `python-test`: Python 3.11 mock/dev dependency를 설치하고 `pytest -q`를 실행한다.
- `docs-script-check`: evidence JSON, PowerShell script help/syntax, lightweight secret pattern scan을 확인한다.
- `compose-mock-smoke`: `compose.yaml` + `compose.dev.yaml`로 mock stack을 띄우고 Spring/Python/Qdrant/MySQL 연결과 active table/schema sanity를 확인한다.
- `docker-build`: backend image와 python-embed mock image를 build only로 확인한다. PR CI에서는 push하지 않는다.

CI는 secrets 없이 돌아야 한다. GHCR login, Firebase/service account, prod SSH secret, real model checkpoint는 CI job에 필요하지 않다.

## Mock Compose 기준

CI compose smoke는 실제 dog-nose-identification2 checkpoint를 사용하지 않는다.

- `EMBED_MODEL=mock-v1`
- `PYTHON_EMBED_INSTALL_REAL_DEPS=0`
- `QDRANT_COLLECTION=dog_nose_embeddings_ci_mock`
- `QDRANT_VECTOR_DIM=128`

이 기준은 연결, migration, Qdrant initializer, Spring to Python 호출, active table 존재 여부를 빠르게 검증하기 위한 mock/dev runtime이다.

## Real-Model 검증

현재 dog nose v2 real-model 기준은 다음과 같다.

- collection: `dog_nose_embeddings_real_v2`
- vector dimension: `2048`
- distance: `Cosine`
- point id: UUID
- metadata tracking: MySQL `dog_nose_references`

실제 model checkpoint가 필요한 E2E는 기본 PR CI에서 실행하지 않는다. `scripts/verify-submission-real-model-e2e.ps1`, `docs/ops-evidence/submission-real-model-e2e-log.md`, `docs/ops-evidence/submission-real-model-e2e-summary.json`가 제출 전 real-model 증적 경로다.

## Publish/CD 범위

`.github/workflows/publish-images.yaml`은 GHCR image publish를 담당한다. `.github/workflows/cd-dev.yaml`과 `.github/workflows/cd-prod.yaml`은 배포 path를 담당한다.

CI 현대화 PR은 publish/CD 동작을 바꾸지 않는다. GHCR publish, dev deploy, prod deploy 경로 변경은 별도 PR에서 다룬다.
