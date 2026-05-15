# Dev CD 검증 로그

> 문서 성격: 운영 증거(Ops Evidence)
>
> shared dev CD 검증 이력을 확인할 때 읽는다.
> active canonical 문서와 충돌하면 active canonical 문서가 우선한다.

## Develop Auto CD 검증

- Date: 2026-05-06
- Purpose: PR -> develop -> CI -> GHCR publish -> shared dev CD 검증
- Result: PASS
- Evidence:
  - PR CI: PASS
  - develop push CI: PASS
  - Publish Images to GHCR: PASS
  - CD - Dev Deploy: PASS
  - External healthcheck: PASS
- Healthcheck:
  - URL: http://43.203.199.224/actuator/health
  - Response: HTTP 200
  - Status: UP
- Notes:
  - self-hosted runner accepted the deploy job
  - deploy.sh completed successfully
  - nginx public HTTP access was enabled through Security Group inbound TCP 80
