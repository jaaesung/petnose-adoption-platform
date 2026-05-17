# Sample Pair Real-Model E2E Smoke Log

## 문서 성격

- local sample image pair runtime evidence다.
- 실행한 명령과 관찰 결과만 기록한다.
- source code/config/test/schema 변경 없음.
- JWT, secret, private path는 문서에 남기지 않는다.
- sample image 개별 경로는 `<sample:milk1>` 같은 label로만 기록한다.
- runtime 결과를 성공처럼 꾸미지 않는다.

## 샘플 이미지

Source folder는 `C:\Dev\sample`이다. 아래에는 개별 파일 경로를 기록하지 않는다.

| Label | Extension | MIME | Readable |
|---|---|---|---|
| `milk1` | `.png` | `image/png` | yes |
| `milk_Profile` | `.png` | `image/png` | yes |
| `milk2` | `.png` | `image/png` | yes |
| `nose3` | `.jpg` | `image/jpeg` | yes |
| `profile2` | `.png` | `image/png` | yes |
| `nose4` | `.jpg` | `image/jpeg` | yes |

## 환경

- Date/time: `2026-05-16 18:59:54 +09:00`
- Branch: `chore/sample-pair-real-model-e2e-smoke`
- Base commit before evidence commit: `a6c9015`
- OS/shell: Windows, PowerShell
- Compose files:
  - `infra/docker/compose.yaml`
  - `infra/docker/compose.dev.yaml`
  - `infra/docker/compose.real-model.yaml`
- Temp env approach:
  - `infra/docker/.env`를 기반으로 repo 밖 temp env file을 만들었다.
  - temp env file path, secret, model host path는 기록하지 않는다.
  - repo의 `infra/docker/.env`는 수정하지 않았다.
- Qdrant collection used: `dog_nose_embeddings_sample_pair_e2e_20260516185447`
- Thresholds used:
  - `QDRANT_SEARCH_SCORE_THRESHOLD=0.70`
  - `NOSE_DUPLICATE_THRESHOLD=0.70`
  - handover `match_threshold=0.92`
  - handover `ambiguous_threshold=0.88`
- Model/dimension observed:
  - Python Embed `model=dog-nose-identification2:s101_224`
  - `vector_dim=2048`
  - `backend=torch+timm`
  - `device=cpu`
  - `model_loaded=true`
- Qdrant config observed:
  - collection status `green`
  - vector size `2048`
  - distance `Cosine`
  - initial `points_count=0`
- Spring dev Qdrant config observed:
  - collection `dog_nose_embeddings_sample_pair_e2e_20260516185447`
  - vector dimension `2048`
  - distance `Cosine`
- Health checks:
  - Spring direct `GET http://localhost:8080/actuator/health`: HTTP `200`, `UP`
  - nginx routed `GET http://localhost/actuator/health`: HTTP `200`, `UP`
  - Python Embed `GET http://localhost:8000/health`: HTTP `200`
  - Qdrant `GET http://localhost:6333/healthz`: HTTP `200`
- Deviation:
  - 이번 실행에서는 nginx 502가 재현되지 않아 nginx restart를 수행하지 않았다.
  - 첫 PowerShell curl harness 시도는 wrapper argument 오류로 HTTP 호출 전 실패했다. 같은 smoke sequence를 수정된 wrapper로 재실행했다.

## 실행 결과 요약

- Result: `PARTIAL`
- Summary:
  - milk1/milk2 duplicate registration: `FAIL`
  - nose3/nose4 duplicate registration: `PASS`
  - milk post + milk2 handover: `FAIL`
  - nose3 post + nose4 handover: `FAIL`
- 중요한 해석:
  - milk2는 `QDRANT_SEARCH_SCORE_THRESHOLD=0.70`에서 milk1 후보를 반환받지 못해 정상 등록되었고 Qdrant point가 추가되었다.
  - 그 결과 milk post handover에서 `<sample:milk2>`의 top match는 expected milk dog가 아니라 앞서 등록된 milk2 dog point가 되었고 `NOT_MATCHED`가 반환되었다.
  - nose3/nose4는 duplicate registration에서는 score `0.80630887 >= 0.70`으로 중복 의심 처리되었지만, handover에서는 score가 handover ambiguous threshold `0.88`보다 낮아 `NOT_MATCHED`가 반환되었다.

## Follow-up implementation after this evidence

- 위 sample-pair smoke는 handover threshold가 `0.92 / 0.88`이던 시점에 실행된 historical runtime evidence다.
- 이 evidence는 dog registration duplicate threshold `0.70`과 handover threshold `0.88 / 0.92`가 sample-pair flow에서 일관되지 않음을 드러냈다.
- 같은 branch의 follow-up implementation은 handover default thresholds를 `0.70 / 0.70`으로 맞춘다.
- 위 milk/nose3 결과는 원본 runtime evidence로 유지한다.
- post-fix runtime retest를 실제로 수행하면 별도 dated subsection에 명령, collection, thresholds, 결과, privacy 확인을 기록한다.
- post-fix runtime을 재실행하지 않은 상태에서는 PASS로 주장하지 않는다.

## Post-fix handover retest - 2026-05-16 19:28 +09:00

### Retest environment

- Result: `PARTIAL`
- Branch: `chore/sample-pair-real-model-e2e-smoke`
- Qdrant collection: `dog_nose_embeddings_handover_070_retest_20260516192811`
- Baseline Qdrant `points_count=0`
- Thresholds:
  - `QDRANT_SEARCH_SCORE_THRESHOLD=0.70`
  - `NOSE_DUPLICATE_THRESHOLD=0.70`
  - `PETNOSE_HANDOVER_VERIFICATION_MATCH_THRESHOLD=0.70`
  - `PETNOSE_HANDOVER_VERIFICATION_AMBIGUOUS_THRESHOLD=0.70`
  - `PETNOSE_HANDOVER_VERIFICATION_TOP_K=5`
- Model/dimension:
  - `dog-nose-identification2:s101_224`
  - `2048`
- Health/config:
  - Spring direct health HTTP `200`, `UP`
  - nginx routed health HTTP `200`, `UP`
  - Python Embed health HTTP `200`
  - Qdrant collection `2048` / `Cosine`
  - Spring dev qdrant config reported the retest collection

### Retest commands

```powershell
# temp env file은 repo 밖에 생성했고 path/content는 redacted
# base env: infra/docker/.env
# overrides included real model, isolated collection, duplicate thresholds 0.70, handover thresholds 0.70/0.70

docker compose --env-file <temp-env-redacted> `
  -f infra\docker\compose.yaml `
  -f infra\docker\compose.dev.yaml `
  -f infra\docker\compose.real-model.yaml `
  up -d --build

docker compose --env-file <temp-env-redacted> `
  -f infra\docker\compose.yaml `
  -f infra\docker\compose.dev.yaml `
  -f infra\docker\compose.real-model.yaml `
  ps

curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8080/actuator/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost/actuator/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8000/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:6333/collections/dog_nose_embeddings_handover_070_retest_20260516192811
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8080/api/dev/qdrant-config
docker exec petnose-spring-api-1 printenv PETNOSE_HANDOVER_VERIFICATION_MATCH_THRESHOLD
docker exec petnose-spring-api-1 printenv PETNOSE_HANDOVER_VERIFICATION_AMBIGUOUS_THRESHOLD
docker exec petnose-spring-api-1 printenv PETNOSE_HANDOVER_VERIFICATION_TOP_K
docker exec petnose-spring-api-1 printenv QDRANT_SEARCH_SCORE_THRESHOLD
docker exec petnose-spring-api-1 printenv NOSE_DUPLICATE_THRESHOLD
```

Runtime API sequence는 PowerShell variable-based curl harness로 실행했다. JWT와 sample image 개별 경로는 기록하지 않는다.

```powershell
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X PATCH "http://localhost:8080/api/users/me/profile" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"display_name":"SampleUser","contact_phone":"01012345678","region":"대구시 달서구"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=MilkDog070" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:milk1>;type=image/png"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"dog_id":"<milk-dog-id>","title":"Milk handover 070 retest <run-id>","content":"Focused handover 0.70 retest content.","status":"OPEN"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts/<milk-post-id>/handover-verifications" -H "Authorization: Bearer <JWT-redacted>" -F "nose_image=@<sample:milk2>;type=image/png"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=NoseDog070" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:nose3>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"dog_id":"<nose3-dog-id>","title":"Nose3 handover 070 retest <run-id>","content":"Focused handover 0.70 retest content.","status":"OPEN"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts/<nose3-post-id>/handover-verifications" -H "Authorization: Bearer <JWT-redacted>" -F "nose_image=@<sample:nose4>;type=image/jpeg"
```

### Retest results

| Pair | Operation | Expected under post-fix policy | Observed score | Threshold | Decision | Pass/Fail | Notes |
|---|---|---|---:|---:|---|---|---|
| milk post + milk2 | handover before any milk2 registration | `MATCHED` only if top match expected and score >= 0.70 | 0.6536445 | 0.70 / 0.70 | `NOT_MATCHED`, `top_match_is_expected=true` | FAIL | model score is below 0.70, so new policy correctly rejects |
| nose3 post + nose4 | handover | `MATCHED` if top match expected and score >= 0.70 | 0.80630887 | 0.70 / 0.70 | `MATCHED`, `matched=true`, `top_match_is_expected=true` | PASS | previous 0.88/0.92 mismatch resolved for this pair |

### Retest privacy/no-mutation

- milk handover response exposed no `nose_image_url`, `top_matched_dog_id`, `author_user_id`, or Qdrant payload details.
- nose3 handover response exposed no `nose_image_url`, `top_matched_dog_id`, `author_user_id`, or Qdrant payload details.
- milk post remained `OPEN` after handover.
- nose3 post remained `OPEN` after handover.
- Qdrant count changed from `0` to `1` after milk1 registration and to `2` after nose3 registration.

## Commands run

### Git setup

```powershell
git status
git fetch origin
git checkout develop
git pull --ff-only origin develop
git branch --show-current
git status
git checkout -b chore/sample-pair-real-model-e2e-smoke
```

### Required docs/config/sample checks

```powershell
Get-Content docs\README.md -Encoding UTF8
Get-Content docs\PROJECT_KNOWLEDGE_INDEX.md -Encoding UTF8
Get-Content docs\PETNOSE_MVP_API_CONTRACT.md -Encoding UTF8
Get-Content docs\PETNOSE_MVP_FINAL_PROJECT_SPEC.md -Encoding UTF8
Get-Content docs\reference\MVP_BACKEND_FLOW_HANDOFF.md -Encoding UTF8
Get-Content docs\reference\DOG_NOSE_MODEL_SCORE_SEMANTICS_AUDIT.md -Encoding UTF8
Get-Content docs\reference\STORAGE_AND_VECTOR_BOUNDARY.md -Encoding UTF8
Get-Content docs\reference\SPRING_PYTHON_EMBED_CONTRACT.md -Encoding UTF8
Get-Content docs\ops-evidence\mvp-real-model-e2e-smoke-log.md -Encoding UTF8
Select-String -Path backend\src\main\resources\application.yml -Pattern 'search-score-threshold|duplicate-threshold|match-threshold|ambiguous-threshold|qdrant|nose|handover' -Context 2,2
Select-String -Path infra\docker\compose.yaml -Pattern 'QDRANT_SEARCH_SCORE_THRESHOLD|NOSE_DUPLICATE_THRESHOLD|MATCH_THRESHOLD|AMBIGUOUS|0\.70|0\.92|0\.88' -Context 1,1
Select-String -Path infra\docker\.env.example -Pattern 'QDRANT_SEARCH_SCORE_THRESHOLD|NOSE_DUPLICATE_THRESHOLD|HANDOVER|MATCH|AMBIGUOUS|0\.70|0\.92|0\.88' -Context 1,1
Select-String -Path backend\src\main\java\com\petnose\api\service\NoseVerificationPolicy.java -Pattern 'duplicateThreshold|>=|maxScore|threshold' -Context 3,4
Select-String -Path backend\src\main\java\com\petnose\api\client\QdrantDogVectorClient.java -Pattern 'score_threshold|scoreThreshold|with_payload|search|points/search' -Context 4,6
rg "PETNOSE_HANDOVER_VERIFICATION|match-threshold|ambiguous-threshold|0\.92|0\.88" backend\src\main\resources backend\src\main\java docs\PETNOSE_MVP_API_CONTRACT.md docs\PETNOSE_MVP_FINAL_PROJECT_SPEC.md
Get-ChildItem <sample-dir> -File
```

### Temp env and runtime startup

```powershell
# temp env file은 repo 밖에 생성했고 path/content는 redacted
# base env: infra/docker/.env
# overrides:
# EMBED_MODEL=dog-nose-identification2
# PYTHON_EMBED_INSTALL_REAL_DEPS=1
# EMBED_DEVICE=cpu
# QDRANT_COLLECTION=dog_nose_embeddings_sample_pair_e2e_20260516185447
# QDRANT_VECTOR_DIM=2048
# QDRANT_DISTANCE=Cosine
# QDRANT_SEARCH_SCORE_THRESHOLD=0.70
# NOSE_DUPLICATE_THRESHOLD=0.70

docker compose --env-file <temp-env-redacted> `
  -f infra\docker\compose.yaml `
  -f infra\docker\compose.dev.yaml `
  -f infra\docker\compose.real-model.yaml `
  up -d --build

docker compose --env-file <temp-env-redacted> `
  -f infra\docker\compose.yaml `
  -f infra\docker\compose.dev.yaml `
  -f infra\docker\compose.real-model.yaml `
  ps

curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8080/actuator/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost/actuator/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8000/health
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:6333/healthz
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:6333/collections/dog_nose_embeddings_sample_pair_e2e_20260516185447
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" http://localhost:8080/api/dev/qdrant-config
docker exec petnose-spring-api-1 printenv QDRANT_SEARCH_SCORE_THRESHOLD
docker exec petnose-spring-api-1 printenv NOSE_DUPLICATE_THRESHOLD
docker exec petnose-spring-api-1 printenv QDRANT_COLLECTION
docker exec petnose-spring-api-1 printenv QDRANT_VECTOR_DIM
```

### Runtime smoke API sequence

아래 명령은 PowerShell 변수 기반 curl harness로 실행했다. 실제 JWT와 sample image 개별 경로는 출력하거나 문서화하지 않았다.

```powershell
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:6333/collections/dog_nose_embeddings_sample_pair_e2e_20260516185447"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/auth/login" -H "Content-Type: application/json" -d '{"email":"<test-email>","password":"<redacted-test-password>"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/users/me" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X PATCH "http://localhost:8080/api/users/me/profile" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"display_name":"SampleUser","contact_phone":"01012345678","region":"대구시 달서구"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=MilkDog" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:milk1>;type=image/png"
curl.exe -sS -o NUL -w "%{http_code}" "http://localhost<returned-file-url>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=MilkDogDuplicateCheck" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:milk2>;type=image/png"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"dog_id":"<milk-dog-id>","title":"Milk sample adoption <run-id>","content":"Milk sample pair smoke content.","status":"OPEN"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=NoseDog" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:nose3>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/dogs/register" -H "Authorization: Bearer <JWT-redacted>" -F "name=NoseDogDuplicateCheck" -F "breed=Mixed" -F "gender=UNKNOWN" -F "nose_image=@<sample:nose4>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts" -H "Authorization: Bearer <JWT-redacted>" -H "Content-Type: application/json" -d '{"dog_id":"<nose3-dog-id>","title":"Nose3 sample adoption <run-id>","content":"Nose3 sample pair smoke content.","status":"OPEN"}'
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts?status=OPEN&page=0&size=20"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<milk-post-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<nose3-post-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/me?page=0&size=20" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<milk-dog-id>" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<nose3-dog-id>" -H "Authorization: Bearer <JWT-redacted>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<milk-dog-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/dogs/<nose3-dog-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts/<milk-post-id>/handover-verifications" -H "Authorization: Bearer <JWT-redacted>" -F "nose_image=@<sample:milk2>;type=image/png"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X POST "http://localhost:8080/api/adoption-posts/<nose3-post-id>/handover-verifications" -H "Authorization: Bearer <JWT-redacted>" -F "nose_image=@<sample:nose4>;type=image/jpeg"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<milk-post-id>"
curl.exe -sS -w "`nHTTP_STATUS:%{http_code}" -X GET "http://localhost:8080/api/adoption-posts/<nose3-post-id>"
```

## API smoke results

| Step | Endpoint | Sample image label used | Expected result | Observed HTTP status | Key observed fields | Pass/Fail | Notes |
|---|---|---|---|---:|---|---|---|
| Spring health | `GET /actuator/health` | n/a | `UP` | 200 | direct `status=UP`, routed `status=UP` | PASS | nginx restart 불필요 |
| Python health | `GET /health` | n/a | real model loaded | 200 | `dog-nose-identification2:s101_224`, `vector_dim=2048` | PASS | `backend=torch+timm` |
| Qdrant config | `GET /collections/{collection}` | n/a | 2048/Cosine | 200 | `status=green`, `points_count=0`, `size=2048`, `distance=Cosine` | PASS | isolated collection |
| Auth/profile | auth + profile endpoints | n/a | one USER ready for both dogs | 201/200 | `role=USER`, `display_name=SampleUser`, phone/region saved | PASS | JWT redacted |
| Baseline count | Qdrant collection metadata | n/a | initial count 0 | 200 | `points_count=0` | PASS | fresh collection |
| milk1 registration | `POST /api/dogs/register` | `milk1` | HTTP 201 registered | 201 | `registration_allowed=true`, `status=REGISTERED`, `verification_status=VERIFIED`, `embedding_status=COMPLETED`, `qdrant_point_id=dog_id`, model real, dimension 2048 | PASS | file serving HTTP 200 for returned nose URL |
| Qdrant count after milk1 | Qdrant collection metadata | n/a | count +1 | 200 | `0 -> 1` | PASS | normal upsert observed |
| milk2 duplicate registration | `POST /api/dogs/register` | `milk2` | HTTP 200 duplicate suspected, score >= 0.70 | 201 | `registration_allowed=true`, `status=REGISTERED`, `max_similarity_score=0.0`, no `top_match` | FAIL | Qdrant count changed `1 -> 2`; milk2 was inserted as a new point |
| milk adoption post creation | `POST /api/adoption-posts` | n/a | HTTP 201 OPEN | 201 | `post_id=3`, `status=OPEN` | PASS | post created for original milk1 dog |
| nose3 registration | `POST /api/dogs/register` | `nose3` | HTTP 201 registered | 201 | `registration_allowed=true`, `status=REGISTERED`, `qdrant_point_id=dog_id`, model real, dimension 2048 | PASS | Qdrant count `2 -> 3` |
| nose4 duplicate registration | `POST /api/dogs/register` | `nose4` | HTTP 200 duplicate suspected, score >= 0.70 | 200 | `registration_allowed=false`, `status=DUPLICATE_SUSPECTED`, `embedding_status=SKIPPED_DUPLICATE`, `qdrant_point_id=null`, score `0.80630887`, top match nose3 dog | PASS | Qdrant count stayed `3` |
| nose3 adoption post creation | `POST /api/adoption-posts` | n/a | HTTP 201 OPEN | 201 | `post_id=4`, `status=OPEN` | PASS | post created for nose3 dog |
| Public post list/detail | `GET /api/adoption-posts`, `GET /api/adoption-posts/{post_id}` | n/a | both posts visible, no nose URL | 200 | both posts found, detail `verification_status=VERIFIED`, profile URL present, no `nose_image_url` | PASS | public privacy passed |
| Dog query privacy | `GET /api/dogs/me`, `GET /api/dogs/{dog_id}` | n/a | owner/public visibility rules | 200 | list contains milk/nose3 dogs, list no `nose_image_url`, `can_create_post=false`, owner detail has nose URL, public detail no nose URL | PASS | after OPEN posts exist |
| milk handover | `POST /api/adoption-posts/{milk_post_id}/handover-verifications` | `milk2` | ideally `MATCHED` | 200 | `decision=NOT_MATCHED`, `matched=false`, `similarity_score=0.9999999`, `threshold=0.92`, `ambiguous_threshold=0.88`, `top_match_is_expected=false` | FAIL | earlier milk2 registration failure created a competing milk2 Qdrant point |
| nose3 handover | `POST /api/adoption-posts/{nose3_post_id}/handover-verifications` | `nose4` | ideally `MATCHED` | 200 | `decision=NOT_MATCHED`, `matched=false`, `similarity_score=0.80630887`, `threshold=0.92`, `ambiguous_threshold=0.88`, `top_match_is_expected=true` | FAIL | duplicate threshold passed, handover threshold did not |
| Handover no auto-complete | `GET /api/adoption-posts/{post_id}` | n/a | status remains OPEN | 200 | milk post `OPEN`, nose3 post `OPEN` | PASS | no completion/status mutation |

## Score summary

| Pair | Operation | Expected | Observed score | Threshold | Decision | Pass/Fail |
|---|---|---|---:|---:|---|---|
| milk1 vs milk2 | duplicate registration | duplicate if score >= 0.70 | 0.0 | 0.70 | `REGISTERED`, `registration_allowed=true`; no candidate returned at Qdrant threshold | FAIL |
| nose3 vs nose4 | duplicate registration | duplicate if score >= 0.70 | 0.80630887 | 0.70 | `DUPLICATE_SUSPECTED`, `registration_allowed=false` | PASS |
| milk post + milk2 | handover | `MATCHED` if score >= 0.92 and top match expected | 0.9999999 | 0.92 / 0.88 | `NOT_MATCHED`, `top_match_is_expected=false` | FAIL |
| nose3 post + nose4 | handover | `MATCHED` if score >= 0.92 and top match expected | 0.80630887 | 0.92 / 0.88 | `NOT_MATCHED`, `top_match_is_expected=true` | FAIL |

## Privacy checks

- Public adoption post list/detail did not expose `nose_image_url`.
- Public adoption post list/detail exposed `profile_image_url` where available and `verification_status`.
- Dog list `GET /api/dogs/me` did not expose `nose_image_url`.
- Owner dog detail exposed `nose_image_url` for the owner-authenticated request.
- Public dog detail did not expose `nose_image_url`.
- Duplicate `top_match` for nose4 did not expose `nose_image_url`.
- Handover responses did not expose:
  - `nose_image_url`
  - `top_matched_dog_id`
  - `author_user_id`
  - Qdrant payload details
  - another dog id beyond `expected_dog_id`

## Data created

- Test user email: `samplepair20260516185447@example.com`
- JWT: captured only in runtime memory; not recorded.
- Dog ids:
  - milk1 dog: `b61e4cac...ec96`
  - milk2 duplicate-check dog that was unexpectedly registered: `dc7ede4d...b95b`
  - nose3 dog: `7ea74485...d48b`
  - nose4 duplicate-check dog: `0a1a000c...9684`
- Adoption post ids:
  - milk post: `3`
  - nose3 post: `4`
- Qdrant collection: `dog_nose_embeddings_sample_pair_e2e_20260516185447`
- Local/dev data can be cleaned up from MySQL, Qdrant, and upload volumes after evidence review.

## Findings

### PASS findings

- Latest develop config uses registration duplicate threshold defaults `0.70` for both Qdrant candidate filtering and Spring duplicate decision.
- Qdrant collection was isolated, empty at baseline, and configured for real model `2048`/`Cosine`.
- Real model runtime was loaded as `dog-nose-identification2:s101_224`.
- nose3/nose4 duplicate detection passed at score `0.80630887 >= 0.70`.
- Public/privacy checks passed for post list/detail, dog list/detail, duplicate `top_match`, and handover response fields.
- Handover verification did not auto-complete or mutate post status.

### PARTIAL/FAIL findings

- milk1/milk2 duplicate detection failed under the current runtime thresholds. The API returned HTTP `201`, `registration_allowed=true`, `max_similarity_score=0.0`, and Qdrant count increased from `1` to `2`.
- milk handover with `<sample:milk2>` returned `NOT_MATCHED` because the top Qdrant match was not the expected milk1 dog. This was influenced by the earlier failed duplicate check, which inserted milk2 as its own registered point.
- nose3/nose4 duplicate detection passed at registration threshold `0.70`, but nose3 handover returned `NOT_MATCHED` because score `0.80630887` is below handover `ambiguous_threshold=0.88` and `match_threshold=0.92`.
- Therefore, duplicate registration success at `>=0.70` is not proof that handover will return `MATCHED`. This is a separate handover policy/threshold follow-up, not proof that the registration duplicate threshold itself failed for the nose3/nose4 pair.

## Follow-ups

- If the milk pair is expected to be same-dog, inspect model/pair score and sample assumptions because no duplicate candidate was returned at threshold `0.70`.
- Original follow-up at smoke time: if product wants handover to accept same-dog different-image pairs that score near the registration duplicate threshold, review handover thresholds separately. That review decision is now implemented in this branch as default handover thresholds `0.70 / 0.70`; the original runtime results above remain historical evidence.
- For Flutter screen smoke, ensure UI handles `DUPLICATE_SUSPECTED`, `NOT_MATCHED`, and top-match-not-expected handover outcomes without treating runtime partials as pass.
- If this isolated collection is reused, clean local/dev MySQL, upload files, and Qdrant collection deliberately before another demo run.
