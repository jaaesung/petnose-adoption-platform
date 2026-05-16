# MVP 백엔드 흐름 smoke 검증 로그

> 문서 성격: 운영 증거(Ops Evidence)
>
> 이 문서는 최신 `develop`에서 분기한 MVP 백엔드 흐름 감사 branch에서 실제 실행한 검증 command만 기록한다. local endpoint curl 또는 dev 서버 호출은 수행하지 않았으므로 기록하지 않는다.

## 검증 개요

- Date: 2026-05-16
- Branch: `chore/mvp-backend-flow-smoke-audit`
- Base: latest `origin/develop` fast-forward 후 분기
- Scope: backend MVP API flow test smoke
- Result: PASS

## 실행 command

Working directory: `backend`

```powershell
gradle test --tests "*Auth*" --tests "*User*" --no-daemon
gradle test --tests "*DogRegistration*" --tests "*DogRegisterAuthPrincipal*" --no-daemon
gradle test --tests "*DogQuery*" --no-daemon
gradle test --tests "*AdoptionPost*" --no-daemon
gradle test --tests "*HandoverVerification*" --no-daemon
gradle clean test --no-daemon
```

## 결과

| Command | Result |
|---|---|
| `gradle test --tests "*Auth*" --tests "*User*" --no-daemon` | PASS, `BUILD SUCCESSFUL` |
| `gradle test --tests "*DogRegistration*" --tests "*DogRegisterAuthPrincipal*" --no-daemon` | PASS, `BUILD SUCCESSFUL` |
| `gradle test --tests "*DogQuery*" --no-daemon` | PASS, `BUILD SUCCESSFUL` |
| `gradle test --tests "*AdoptionPost*" --no-daemon` | PASS, `BUILD SUCCESSFUL` |
| `gradle test --tests "*HandoverVerification*" --no-daemon` | PASS, `BUILD SUCCESSFUL` |
| `gradle clean test --no-daemon` | PASS, `BUILD SUCCESSFUL` |

## 비고

- Gradle wrapper file은 repository에 없어서 Windows local equivalent인 `gradle` command를 `backend` directory에서 사용했다.
- 모든 command는 local test suite 기반 검증이다.
- Firebase/chat/push, reservation/payment/contract, report/admin API 검증은 수행 대상이 아니다.
