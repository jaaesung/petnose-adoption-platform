# MVP Real-Model E2E Smoke Log

## 문서 성격

- 실제 local/dev runtime smoke evidence다.
- 실행한 명령과 관찰한 결과만 기록한다.
- 실행하지 않은 curl 결과를 작성하지 않는다.
- secret, token, private host path는 문서에 남기지 않는다.
- JWT access token은 runtime 중 메모리 변수로만 사용했고 문서에는 `redacted`로 기록한다.
- local dog nose image와 model host path는 task 관련 로컬 자산이지만 private path로 보고 문서에는 `redacted-local-dog-nose-dataset-jpeg`, `redacted-local-model-path`로 기록한다.

## 환경

- Date/time: `2026-05-16 16:07:51 +09:00`
- Branch: `chore/mvp-real-model-e2e-smoke-evidence`
- Base commit before evidence commit: `8fce089`
- OS/shell: Windows, PowerShell
- Docker runtime: Docker Desktop Linux engine
- Compose command:
  - `docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml up -d --build`
- Compose overlays:
  - `infra/docker/compose.yaml`
  - `infra/docker/compose.dev.yaml`
  - `infra/docker/compose.real-model.yaml`
- Active runtime assumptions observed from sanitized local env/config:
  - `SPRING_PROFILES_ACTIVE=dev`
  - `EMBED_MODEL=dog-nose-identification2`
  - `PYTHON_EMBED_INSTALL_REAL_DEPS=1`
  - `EMBED_DEVICE=cpu`
  - `QDRANT_COLLECTION=dog_nose_embeddings_real_v1`
  - `QDRANT_VECTOR_DIM=2048`
  - `QDRANT_DISTANCE=Cosine`
  - `NOSE_DUPLICATE_THRESHOLD=0.95`
  - `UPLOAD_BASE_PATH=/var/uploads`
- Service endpoints used:
  - Spring direct: `http://localhost:8080`
  - Nginx/file serving: `http://localhost`
  - Python Embed: `http://localhost:8000`
  - Qdrant: `http://localhost:6333`
  - MySQL host mapping: `localhost:3308 -> mysql:3306`
- Real model observed:
  - Python health `model=dog-nose-identification2:s101_224`
  - Python health `vector_dim=2048`
  - Python health `backend=torch+timm`
  - Python health `model_loaded=true`
  - Python health `model_path_exists=true`
- Qdrant observed:
  - Collection `dog_nose_embeddings_real_v1`
  - Vector size `2048`
  - Distance `Cosine`

## 실행 결과 요약

- Result: `PASS`
- Summary: local/dev real-model stack에서 auth, profile, dog registration 정상/중복, dog query, adoption post, handover verification, status management, file serving smoke를 완료했다.
- Deviation: `up --build` 과정에서 `spring-api`가 recreate된 뒤 nginx가 stale upstream으로 최초 `GET http://localhost/actuator/health`에 `502 Bad Gateway`를 반환했다. `nginx`만 restart한 뒤 같은 health check는 `UP`으로 통과했다.
- Existing local data note: smoke 시작 시 Qdrant collection에는 기존 point 1개가 있었다. 이번 정상 등록 후 points_count는 2가 되었고, duplicate suspected 재등록 후에도 2로 유지되어 duplicate upsert skip을 확인했다.

## Commands run

### Git setup

```powershell
git status
git fetch origin
git checkout develop
git pull --ff-only origin develop
git branch --show-current
git status
git checkout -b chore/mvp-real-model-e2e-smoke-evidence
```

### Required docs and environment inventory

```powershell
Test-Path docs\README.md
Test-Path docs\PROJECT_KNOWLEDGE_INDEX.md
Test-Path docs\reference\MVP_BACKEND_FLOW_HANDOFF.md
Test-Path docs\ops-evidence\mvp-backend-flow-smoke-log.md
Get-Content docs\README.md -Encoding UTF8
Get-Content docs\PROJECT_KNOWLEDGE_INDEX.md -Encoding UTF8
Get-Content docs\PETNOSE_MVP_API_CONTRACT.md -Encoding UTF8
Get-Content docs\PETNOSE_MVP_FINAL_PROJECT_SPEC.md -Encoding UTF8
Get-Content docs\reference\MVP_BACKEND_FLOW_HANDOFF.md -Encoding UTF8
Get-Content docs\reference\STORAGE_AND_VECTOR_BOUNDARY.md -Encoding UTF8
Get-Content docs\reference\SPRING_PYTHON_EMBED_CONTRACT.md -Encoding UTF8
Get-Content docs\ops-evidence\mvp-backend-flow-smoke-log.md -Encoding UTF8
rg --files -g compose*.yaml -g compose*.yml -g *README* -g application*.yml -g application*.yaml -g *.properties -g Dockerfile* -g .env.example
Get-Content infra\docker\compose.yaml -Encoding UTF8
Get-Content infra\docker\compose.dev.yaml -Encoding UTF8
Get-Content infra\docker\compose.real-model.yaml -Encoding UTF8
Get-Content backend\src\main\resources\application.yml -Encoding UTF8
Get-Content infra\docker\.env.example -Encoding UTF8
Get-Content README.md -Encoding UTF8
Get-Content backend\README.md -Encoding UTF8
Get-Content python-embed\README.md -Encoding UTF8
Get-Content infra\scripts\dev-up.sh -Encoding UTF8
Get-Content infra\scripts\healthcheck.sh -Encoding UTF8
Get-Content infra\nginx\conf.d\default.conf -Encoding UTF8
Get-Content python-embed\Dockerfile -Encoding UTF8
Test-Path infra\docker\.env
```

Sanitized local env inventory command was run against `infra/docker/.env`; secret values and private host paths are not recorded in this document.

### Docker startup and health

```powershell
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml ps
Get-Command docker
Get-Service com.docker.service
Test-Path 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
Start-Service com.docker.service
Start-Process -FilePath 'C:\Program Files\Docker\Docker\Docker Desktop.exe' -WindowStyle Hidden
docker info
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml up -d --build
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml ps
curl.exe -s http://localhost:8080/actuator/health
curl.exe -s http://localhost/actuator/health
curl.exe -s http://localhost:8000/health
curl.exe -s http://localhost:6333/healthz
curl.exe -s http://localhost:8080/api/dev/qdrant-config
curl.exe -s http://localhost:6333/collections/dog_nose_embeddings_real_v1
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml logs --tail 80 nginx
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml -f infra/docker/compose.real-model.yaml restart nginx
curl.exe -s http://localhost/actuator/health
```

Docker notes:

- 최초 `docker compose ps`는 Docker daemon pipe unavailable로 실패했다.
- `Start-Service com.docker.service`는 권한 부족으로 실패했다.
- Docker Desktop launcher 실행 후 `docker info`가 `docker-ready`가 되었고 compose stack을 시작했다.

### Runtime smoke curl sequence

아래 endpoint들을 PowerShell 변수 기반 curl harness로 실행했다. 실제 JWT와 private image path는 출력하지 않았다.

```powershell
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:6333/collections/dog_nose_embeddings_real_v1"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/users/me" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X PATCH "http://localhost:8080/api/users/me/profile" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"display_name":"SmokeUser","contact_phone":"01012345678","region":"대구시 달서구"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/users/me" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=SmokeDog" -F "breed=Mixed" -F "gender=UNKNOWN" -F "description=real model smoke first registration" -F "nose_image=@<redacted-local-dog-nose-dataset-jpeg>;type=image/jpeg" -F "profile_image=@<redacted-local-dog-nose-dataset-jpeg>;type=image/jpeg"
curl.exe -sS -o NUL -w "%{http_code}" "http://localhost<first-registration-nose-image-url>"
curl.exe -sS -o NUL -w "%{http_code}" "http://localhost<first-registration-profile-image-url>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:6333/collections/dog_nose_embeddings_real_v1"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=SmokeDogDup" -F "breed=Mixed" -F "gender=UNKNOWN" -F "description=real model smoke duplicate registration" -F "nose_image=@<redacted-local-dog-nose-dataset-jpeg>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:6333/collections/dog_nose_embeddings_real_v1"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/me?page=0&size=20" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<dog-id>" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<dog-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"dog_id":"<dog-id>","title":"Smoke adoption post <run-id>","content":"Real model MVP smoke content.","status":"OPEN"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts?status=OPEN&page=0&size=20"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<post-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/me?page=0&size=20" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts/<post-id>/handover-verifications" -H "Authorization: Bearer <JWT-redacted>" -F "nose_image=@<redacted-local-dog-nose-dataset-jpeg>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<post-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X PATCH "http://localhost:8080/api/adoption-posts/<post-id>/status" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"status":"RESERVED"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X PATCH "http://localhost:8080/api/adoption-posts/<post-id>/status" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"status":"COMPLETED"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<dog-id>" -H "Authorization: Bearer <JWT-redacted>"
```

Failed harness attempt:

- 한 번의 PowerShell curl helper가 argument binding 오류로 `curl: (2) no URL specified`를 반환했고, HTTP endpoint 호출 전 실패했다.
- 같은 smoke sequence를 helper 수정 후 재실행했다.

## API smoke results

| Step | Endpoint | Request summary | Expected result | Observed result | Pass/Fail | Notes |
|---|---|---|---|---|---|---|
| Backend health | `GET /actuator/health` | Spring direct | healthy status | HTTP 200, `status=UP`, MySQL component `UP` | PASS | `http://localhost:8080` |
| Nginx health | `GET /actuator/health` | nginx route | healthy status | 최초 HTTP 502, nginx restart 후 HTTP 200 `UP` | PASS with deviation | stale upstream after `spring-api` recreate |
| Python Embed health | `GET /health` | Python direct | real model loaded if exposed | HTTP 200, `model_loaded=true`, `model=dog-nose-identification2:s101_224`, `vector_dim=2048`, `backend=torch+timm` | PASS | Spring flow used Python through backend for registration/handover |
| Qdrant health/config | `GET /healthz`, `GET /collections/dog_nose_embeddings_real_v1`, `GET /api/dev/qdrant-config` | Qdrant direct and Spring dev config | collection/dimension match real model | Qdrant healthy, collection green, Spring config `collection=dog_nose_embeddings_real_v1`, `vector_dimension=2048` | PASS | collection had 1 pre-existing point |
| Auth register | `POST /api/auth/register` | test email/password | HTTP 201, role USER | HTTP 201, `role=USER`, optional profile fields null | PASS | test email `smoke20260516160732@example.com` |
| Auth login | `POST /api/auth/login` | test email/password | HTTP 200 with Bearer token | HTTP 200, `token_type=Bearer`, `expires_in=3600` | PASS | access token captured, redacted |
| Current user before profile | `GET /api/users/me` | Bearer JWT | current user payload | HTTP 200, `role=USER`, profile fields null | PASS | password/token not exposed |
| Profile update | `PATCH /api/users/me/profile` | `display_name=SmokeUser`, `contact_phone=01012345678`, `region=대구시 달서구` | hardened profile accepted | HTTP 200, fields saved | PASS | follow-up GET reflected same values |
| Dog first registration | `POST /api/dogs/register` | multipart with local dog nose JPEG and profile JPEG | HTTP 201 normal registration | HTTP 201, `registration_allowed=true`, `status=REGISTERED`, `verification_status=VERIFIED`, `embedding_status=COMPLETED`, model `dog-nose-identification2:s101_224`, dimension `2048`, `qdrant_point_id=dog_id` | PASS | dog id redacted in summary |
| File serving | `GET /files/...` via nginx | URLs from registration response | uploaded files reachable | nose image HTTP 200, profile image HTTP 200 | PASS | URL paths were upload-root-relative derived URLs |
| Qdrant after first registration | `GET /collections/dog_nose_embeddings_real_v1` | collection metadata | point inserted | points_count `1 -> 2` | PASS | normal registration upsert observed by count |
| Dog duplicate registration | `POST /api/dogs/register` | same nose JPEG, valid JWT | HTTP 200 duplicate suspected | HTTP 200, `registration_allowed=false`, `status=DUPLICATE_SUSPECTED`, `verification_status=DUPLICATE_SUSPECTED`, `embedding_status=SKIPPED_DUPLICATE`, `qdrant_point_id=null`, `top_match` exists | PASS | `top_match` had no `nose_image_url` |
| Qdrant after duplicate | `GET /collections/dog_nose_embeddings_real_v1` | collection metadata | duplicate upsert skipped if count evidence available | points_count stayed `2` | PASS | count evidence supports upsert skip |
| Dog list | `GET /api/dogs/me?page=0&size=20` | Bearer JWT | registered dog appears, can create post, no nose URL | HTTP 200, created dog found, `can_create_post=true`, no `nose_image_url` field in item | PASS | total_count 2 because duplicate attempt also created local dog record |
| Owner dog detail | `GET /api/dogs/{dog_id}` | Bearer JWT | owner detail may expose nose URL | HTTP 200, owner dog detail included `nose_image_url`, `can_create_post=true` | PASS | owner-scoped exposure only |
| Public dog detail before post | `GET /api/dogs/{dog_id}` | no JWT, before public post | `DOG_NOT_ACCESSIBLE` | HTTP 403, `error_code=DOG_NOT_ACCESSIBLE` | PASS | common error shape observed |
| Adoption post create | `POST /api/adoption-posts` | owner JWT, status `OPEN` | HTTP 201 and post id | HTTP 201, `status=OPEN`, `published_at` present, `post_id=2` | PASS | no DRAFT update needed |
| Public post list | `GET /api/adoption-posts?status=OPEN&page=0&size=20` | public | created post visible, no nose URL | HTTP 200, created post found, `verification_status=VERIFIED`, `profile_image_url` present, no `nose_image_url` | PASS | total_count 2 because local/dev had existing post |
| Public post detail | `GET /api/adoption-posts/{post_id}` | public | no nose URL, verification status present | HTTP 200, `status=OPEN`, `verification_status=VERIFIED`, `profile_image_url` present, no `nose_image_url` | PASS | public privacy check |
| Current user's posts | `GET /api/adoption-posts/me?page=0&size=20` | Bearer JWT | current user's post appears | HTTP 200, created post found, `status=OPEN`, no `nose_image_url` | PASS | total_count 1 for test user |
| Handover verification | `POST /api/adoption-posts/{post_id}/handover-verifications` | Bearer JWT, same nose JPEG | one allowed decision, same image expected MATCHED | HTTP 200, `decision=MATCHED`, `matched=true`, score `1.0`, threshold `0.92`, ambiguous threshold `0.88`, model real, dimension `2048` | PASS | response had no nose URL, top matched dog id field, author user id, or Qdrant payload |
| Handover no auto completion | `GET /api/adoption-posts/{post_id}` | public after handover | status remains unchanged | HTTP 200, `status=OPEN` | PASS | no automatic completion |
| Status to RESERVED | `PATCH /api/adoption-posts/{post_id}/status` | owner JWT, `RESERVED` | HTTP 200 RESERVED | HTTP 200, `status=RESERVED` | PASS | run after handover |
| Status to COMPLETED | `PATCH /api/adoption-posts/{post_id}/status` | owner JWT, `COMPLETED` | HTTP 200 COMPLETED | HTTP 200, `status=COMPLETED`, `closed_at` present | PASS | terminal state |
| Dog after completion | `GET /api/dogs/{dog_id}` | owner JWT | service behavior may set dog ADOPTED | HTTP 200, `dogs.status=ADOPTED`, `can_create_post=false` | PASS | observed through API |

## Privacy checks

- Dog list `GET /api/dogs/me` did not expose `nose_image_url`.
- Owner dog detail exposed `nose_image_url` only with owner JWT.
- Public dog detail before public post returned `DOG_NOT_ACCESSIBLE`.
- Public adoption post list did not expose `nose_image_url`.
- Public adoption post detail did not expose `nose_image_url`.
- Current user's adoption post list did not expose `nose_image_url`.
- Duplicate suspected `top_match` existed and did not expose `nose_image_url`.
- Handover verification response did not expose:
  - `nose_image_url`
  - `top_matched_dog_id`
  - another dog id field beyond expected dog workflow field
  - Qdrant payload details
  - `author_user_id`

## Data created

- User email: `smoke20260516160732@example.com`
- JWT: captured for runtime calls, redacted and not committed.
- Normal dog id: `97d05093...f250`
- Duplicate suspected dog: created by duplicate registration attempt; id not recorded in this document.
- Adoption post id: `2`
- Local/dev data can be cleaned up from MySQL, Qdrant, and upload volume if a fresh demo dataset is required.

## Blockers / deviations

- Docker daemon was not initially available:
  - `docker compose ... ps` failed with Docker Desktop Linux engine pipe unavailable.
  - `Start-Service com.docker.service` failed due permission to open the service.
  - `Start-Process ... Docker Desktop.exe` succeeded, and `docker info` then returned ready.
- Nginx stale upstream after `spring-api` recreate:
  - initial routed health through `http://localhost/actuator/health` returned `502 Bad Gateway`.
  - `docker compose ... restart nginx` fixed routing and the same endpoint returned `UP`.
- One PowerShell curl harness attempt failed before HTTP execution with `curl: (2) no URL specified`; the helper was corrected and the smoke sequence was rerun successfully.
- No product source, tests, DB schema, SQL, DBML, or migration file was changed to run this smoke.

## Follow-ups

- Flutter real screen smoke should run against the same backend endpoints using the app's real token storage and upload UI.
- Firebase/chat teammate review boundary remains a separate future design/review area and was not implemented here.
- If the demo requires a pristine dataset, decide a safe local/dev data reset procedure before rehearsal.
- Consider documenting the nginx restart workaround for local `up --build` when `spring-api` is recreated and nginx keeps a stale upstream address.
