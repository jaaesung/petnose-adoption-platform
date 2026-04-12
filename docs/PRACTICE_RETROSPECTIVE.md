# 연습 레포 구축 회고

이 문서는 `petnose-adoption-platform` 연습 레포를 처음부터 구축하면서 겪은 시행착오와 교훈을 기록합니다.  
실제 졸업작품 레포를 구축할 때 같은 실수를 반복하지 않도록 하는 것이 목적입니다.

---

## 이번 연습의 목적

- Spring Boot + Python FastAPI + Qdrant + MySQL + Nginx의 멀티 서비스 monorepo 구조를 Docker Compose로 통합 실행하는 방법 습득
- GitHub Actions CI를 실제로 통과시키는 경험
- 팀원이 clone 후 바로 시작할 수 있는 수준의 저장소 구성 방법 학습

---

## 처음 상태 요약

- 32개 파일로 구성된 monorepo 스캐폴딩만 있었음 (코드는 있지만 실행 안 됨)
- Docker Compose가 실제로 기동되지 않는 상태
- GitHub remote 연결 없음
- CI 없음

---

## 실제로 겪은 문제들

---

### 문제 1: Git 루트 위치 오류 (가장 크리티컬)

**현상**  
`git init`이 `C:/Dev/`에서 실행되어, 실제 프로젝트 디렉토리가 `petnose-adoption-platform/` 하위 디렉토리로 커밋됨.  
GitHub에 push하면 `.github/workflows/` 가 저장소 루트에 없어 GitHub Actions가 아예 트리거되지 않음.

**원인**  
`git init`을 프로젝트 폴더 안이 아닌 부모 폴더에서 실행.

**해결**  
임시 클론(`/c/Dev/_petnose_fix`) 생성 → `cp -r petnose-adoption-platform/. .` 로 파일 루트로 이동 → 재커밋 후 push.

**교훈**  
> 가장 먼저 할 것: `git init`은 반드시 프로젝트 루트 디렉토리 안에서 실행.  
> `git rev-parse --show-toplevel`로 git 루트 위치를 항상 확인하라.

---

### 문제 2: Qdrant 이미지에 curl 없음 (healthcheck 실패)

**현상**  
`compose.yaml`의 Qdrant healthcheck에서 `curl -sf http://localhost:6333/healthz` 사용 → 컨테이너 내부에 curl 없음 → `unhealthy` 상태 지속.

**원인**  
`qdrant/qdrant:v1.9.2`는 Debian 기반이지만 curl이 설치되어 있지 않은 minimal 이미지.

**해결**  
```yaml
test: ["CMD-SHELL", "bash -c 'echo > /dev/tcp/localhost/6333' 2>/dev/null && echo ok || exit 1"]
```
bash의 `/dev/tcp` 가상 파일을 이용해 포트 연결 여부만 확인.

**교훈**  
> 서드파티 이미지의 healthcheck를 작성하기 전에 `docker run --rm <image> which curl` 로 도구 존재 여부를 먼저 확인하라.  
> curl/wget 없는 이미지에서는 `/dev/tcp` 또는 python `-c "import urllib.request..."` 방식을 사용한다.

---

### 문제 3: MySQL healthcheck의 `$VAR` vs `$$VAR`

**현상**  
Compose `CMD-SHELL`에서 `$MYSQL_USER`를 사용하면 Compose가 먼저 host 변수로 치환 시도 → 컨테이너 환경변수를 참조하지 못함.

**원인**  
Compose는 `$VAR`를 host 환경변수로 치환한다. 컨테이너 내부 환경변수를 쓰려면 `$$VAR` (달러 두 개)로 이스케이프해야 함.

**해결**  
```yaml
test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u$$MYSQL_USER -p$$MYSQL_PASSWORD --silent"]
```

**교훈**  
> Compose의 `CMD-SHELL` healthcheck에서 컨테이너 환경변수를 참조할 때는 반드시 `$$VAR` 사용.

---

### 문제 4: `org.hibernate.dialect.MySQL8Dialect` — Hibernate 6에서 제거됨

**현상**  
Spring Boot 3.2.x는 Hibernate 6.4.x를 사용하는데, `MySQL8Dialect`가 Hibernate 6에서 제거됨.  
`application.yml`에 명시적으로 선언했더니 CI에서 `BeanCreationException` 발생.

**원인**  
Hibernate 5 → 6 마이그레이션에서 기존 `MySQL8Dialect`, `H2Dialect` 클래스명이 변경/제거됨.  
Spring Boot 3.x는 JDBC URL에서 dialect를 자동 감지하므로 명시 불필요.

**해결**  
`application.yml`에서 `dialect` 설정 라인을 완전히 제거.

**교훈**  
> Spring Boot 3.x + Hibernate 6 조합에서는 `spring.jpa.properties.hibernate.dialect`를 명시하지 마라.  
> 자동 감지에 맡기는 것이 정답.

---

### 문제 5: `DevController` `@Profile` 누락 → `contextLoads()` 실패

**현상**  
`@SpringBootTest @ActiveProfiles("test")`로 실행되는 `contextLoads()`에서 `BeanCreationException` 발생.  
로그: `PropertyPlaceholderHelper` → `Could not resolve placeholder 'spring.application.name'`

**원인**  
`DevController`에 `@Profile` 없이 `@Value("${spring.application.name}")` 선언.  
test profile의 `application.yml`에 `spring.application.name` 키가 없어 placeholder 해석 실패.

**해결** (3중 방어)
1. `@Profile("dev")` 추가 → test 컨텍스트에서 Bean 자체를 생성하지 않음 (주 수정)
2. `@Value("${spring.application.name:petnose-api}")` 기본값 추가 (방어)
3. test `application.yml`에 `spring.application.name: petnose-api` 추가 (방어)

**교훈**  
> dev 전용 컨트롤러/Bean에는 반드시 `@Profile("dev")` 어노테이션을 붙여라.  
> `@Value`에는 `:defaultValue` 형태로 기본값을 항상 제공하라.  
> 특히 test profile로 돌리는 `contextLoads()`는 외부 서비스 없이도 통과해야 한다.

---

### 문제 6: 로컬 Java 17 vs 프로젝트 Java 21 불일치

**현상**  
로컬에서 `gradle test` 실행 시 `error: invalid source release: 21` 발생.

**원인**  
`build.gradle.kts`에 `sourceCompatibility = JavaVersion.VERSION_21` 설정이 있는데, 로컬 `JAVA_HOME`이 Java 17을 가리키고 있었음.

**상황**  
CI(`actions/setup-java@v4` + `java-version: '21'`)는 Java 21을 사용하므로 CI는 정상 통과.  
로컬 검증이 Java 버전 불일치로 불가능한 상태였음.

**교훈**  
> 새 저장소를 구성할 때 로컬 Java 버전과 `build.gradle.kts`의 `sourceCompatibility`를 먼저 맞춰라.  
> CI와 로컬 환경이 달라지면 "CI에서만 검증 가능"한 불편한 상황이 된다.

---

### 문제 7: GitHub Actions 로그를 API로 볼 수 없음

**현상**  
CI 실패 원인을 파악하기 위해 `curl https://api.github.com/repos/.../actions/runs/.../logs`를 시도했으나 403 응답.

**원인**  
GitHub Actions 로그 다운로드는 인증(Personal Access Token 또는 `gh auth login`)이 필요.

**교훈**  
> `gh auth login`으로 GitHub CLI를 미리 인증해두면 `gh run view`, `gh run watch`, `gh run download` 등으로 CI 로그를 직접 확인할 수 있다.  
> 또는 CI yaml에 `--stacktrace` 옵션을 추가해 실패 원인이 GitHub UI에서도 보이도록 만들어라.

---

## GitHub Actions 관련 추가 시행착오

| 문제 | 원인 | 해결 |
|------|------|------|
| CI가 전혀 트리거 안 됨 | Git 루트 위치 오류 (문제 1) | 파일 구조 재배치 |
| `gradle test` 실패 | Hibernate 6 dialect 이슈 (문제 4) | dialect 제거 |
| `contextLoads()` 실패 | `@Profile` 누락 + placeholder (문제 5) | `@Profile("dev")` + 기본값 |
| Node 20 deprecation 경고 | `gradle/actions/setup-gradle@v3` 내부 Node 버전 | TODO 주석 → 향후 `@v4` 업그레이드 |

---

## "실전 레포에서는 이렇게 하지 말 것"

1. **부모 디렉토리에서 `git init` 하지 마라** — 나중에 고치는 비용이 매우 크다.
2. **`@Profile` 없이 dev 전용 Bean을 만들지 마라** — test 컨텍스트 오염이 생긴다.
3. **Hibernate dialect를 Spring Boot 3.x에서 명시하지 마라** — 자동 감지가 더 안전하다.
4. **로컬 Java 버전을 확인하지 않고 `sourceCompatibility`를 올리지 마라** — 로컬 빌드가 불가능해진다.
5. **서드파티 이미지 healthcheck에 curl을 무조건 쓰지 마라** — 이미지에 curl이 없을 수 있다.
6. **CI를 구성하기 전에 로컬 테스트를 먼저 통과시켜라** — CI 실패 원인을 원격에서 디버깅하는 것은 비효율적이다.

---

## 다음 실전 레포에서 권장하는 진행 순서

1. 프로젝트 폴더 생성 → **그 안에서** `git init` → GitHub 연결
2. `java -version` 확인 → `build.gradle.kts` `sourceCompatibility` 맞춤
3. 로컬에서 `gradle test` 통과 확인 후 CI 작성
4. dev 전용 코드에 처음부터 `@Profile("dev")` 붙이기
5. `@Value`에 항상 기본값 포함
6. Docker healthcheck 작성 전 이미지 내부 도구 확인
7. CI 통과 확인 → branch protection 설정 → 팀원 초대
