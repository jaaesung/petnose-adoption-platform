# Backup/Restore Drill Log

이 문서는 `docs/BACKUP_PLAN.md`의 drill을 실행한 결과를 기록합니다.
민감정보(비밀번호/토큰/원본 개인정보)는 기록하지 않습니다.

## Template

```text
DateTime (KST):
Operator:
Environment: dev | prod

MySQL backup file:
Uploads backup file:

Mutation before restore:
- DB expected mutated value:
- uploads expected mutated value/hash:

Verification after restore:
- DB query result:
- uploads content/hash:
- healthcheck result:

Final verdict: PASS | FAIL
Failure notes (if FAIL):
```

---

## Runs

<!-- Append newest run at top -->

