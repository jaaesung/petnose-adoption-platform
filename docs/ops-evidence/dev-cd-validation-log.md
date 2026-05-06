# Dev CD Validation Log

## Develop Auto CD Validation

- Date: 2026-05-06
- Purpose: validate PR -> develop -> CI -> GHCR publish -> shared dev CD
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