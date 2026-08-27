# Repository Guidelines

AI 에이전트(Codex · Claude 등)가 이 저장소에서 작업할 때 따라야 할 규약입니다.
상세 규칙은 [CLAUDE.md](CLAUDE.md)와 [docs/domain-guide.md](docs/domain-guide.md)를 기준으로 삼고,
이 문서는 **어떤 도구로 작업하든 반드시 지켜야 할 것**만 정리합니다.

## Project Overview

Movi는 시각장애인·시니어가 **화면 없이 음성만으로** 은행 업무를 볼 수 있게 하는 Voice-First 뱅킹 플랫폼입니다.
오픈뱅킹 API로 잔액조회·이체를 처리하고, Isolation Forest 기반 FDS로 이상 거래를 탐지해 보호자에게 알립니다.

**Java 21 · Spring Boot 4.1.0 · Gradle 9.5.1 · MySQL · 단일 모듈**

---

## 먼저 읽어야 할 것

작업 전에 해당하는 문서를 확인합니다. 추측으로 구조를 만들지 않습니다.

| 문서 | 언제 |
|---|---|
| **[docs/integration-spec.md](docs/integration-spec.md)** | **파트 간 계약 — 충돌 시 최우선 기준** |
| [docs/ai-api-contract.md](docs/ai-api-contract.md) | AI Voice·FDS 내부 API를 호출할 때 |
| [docs/execution-plan.md](docs/execution-plan.md) | 오늘 무엇을 해야 하는지 |
| [docs/ERD.md](docs/ERD.md) · [docs/schema.sql](docs/schema.sql) | 테이블·컬럼을 다룰 때 |
| [docs/domain-guide.md](docs/domain-guide.md) | 도메인 로직·불변식·테스트를 쓸 때 |
| [docs/error-codes.md](docs/error-codes.md) | 예외를 던질 때 |
| [docs/api-response.md](docs/api-response.md) | 컨트롤러 응답을 만들 때 |
| [docs/schedule-backend.md](docs/schedule-backend.md) | 담당·우선순위가 궁금할 때 |

---

## 이미 있는 것 — 새로 만들지 마세요

**이 절이 이 문서에서 가장 중요합니다.** 아래는 팀 3명이 공유하는 자산입니다. 중복 생성하면 머지 시 충돌합니다.

| 자산 | 위치 | 규모 |
|---|---|---|
| 도메인 엔티티 | `domain/{도메인}/entity/` | 20개 (테이블과 1:1) |
| enum | `domain/{도메인}/type/` | 17개 |
| 에러 코드 | `global/error/ErrorCode` | 59개 |
| 공통 응답 | `global/response/ApiResponse`, `PageResponse` | |
| 인증 컨텍스트 | `global/security/AuthUser`, `@CurrentUser` | |
| 시각 자동화 | `global/entity/BaseTimeEntity`, `BaseCreatedEntity` | |

필요한 엔티티가 없어 보이면 **먼저 `domain/` 아래를 검색**하세요. `User`, `Account`, `Transfer` 등은 전부 있습니다.
정말 없는 것을 추가할 때는 `docs/schema.sql`과 `docs/ERD.md`를 함께 갱신합니다.

---

## 이 프로젝트에서 협상 불가한 것

**돈이 움직이고, 사용자가 화면을 볼 수 없습니다.**

1. **AI 추출 결과를 신뢰하지 않는다** — 음성 intent·entity는 AI 파트가 추출하지만 **필수값 검증과 실행 판단은 백엔드 책임**입니다. AI가 금액을 환각으로 채우거나 놓쳐도 이체 API에서 막혀야 합니다. **슬롯·확인 문장·세션은 백엔드가 단일 소유자**이며, AI와 프론트는 보관하지 않습니다.
2. **이체는 멱등성이 필수** — 음성은 오인식·중복 발화가 잦습니다. `idempotency_key`로 중복을 차단합니다.
3. **FDS 분기는 세 갈래** — `LOW→ALLOW` / `MEDIUM→ALLOW_WITH_ALERT` / `HIGH→BLOCK`. 보호자 승인 기능은 MVP에서 제외했고 알림만 받습니다. **FDS 평가에 실패하면 이체를 통과시키지 않습니다**(타임아웃 3초, 자동 재시도 없음). 임계값은 AI가 정하며 백엔드가 재계산하지 않습니다.
4. **민감정보는 암호화·마스킹** — 계좌번호·전화번호·토큰을 로그에 원문으로 남기지 않습니다. `toString()`에도 넣지 않습니다.
5. **오류도 음성으로 안내된다** — 예외 메시지가 TTS로 읽힙니다. 스택 트레이스나 영문 기술 용어가 그대로 나가면 안 됩니다.

---

## Build & Test Commands

```bash
./gradlew build                                           # 전체 빌드 + 테스트
./gradlew test                                            # 테스트만
./gradlew test --tests "TransferServiceTest"              # 단일 테스트
./gradlew bootRun --args='--spring.profiles.active=local' # 로컬 실행
```

**작업을 마치기 전 반드시 `./gradlew build`를 통과시킵니다.** 빌드가 깨진 채로 커밋하지 않습니다.

### 최초 세팅

`*.yml`은 gitignore 대상이라 clone 직후에는 설정 파일이 없습니다. `.example` 템플릿은 쓰지 않으므로 `application.yml`, `application-local.yml`, `application-test.yml`을 직접 만들고, 채워야 할 값은 팀 채널(Notion/카톡)에서 확인합니다.

```bash
mysql -u root -p -e "CREATE DATABASE movi CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p movi < docs/schema.sql
```

### 엔티티를 수정했다면

`ddl-auto: validate`라 엔티티와 DB 스키마가 어긋나면 **기동 자체가 실패**합니다. 의도된 장치입니다.

1. `docs/schema.sql` 수정
2. DB에 반영
3. `docs/ERD.md`와 ERDCloud용 SQL도 갱신

---

## 프로젝트 구조

```text
com.movi_backend/
├── domain/{auth,account,transfer,voice,fds,guardian}/
│   ├── controller/         # REST 엔드포인트
│   ├── application/        # 비즈니스 로직 (*Service, *Facade)
│   ├── repository/
│   ├── entity/             # JPA 엔티티
│   ├── type/               # enum
│   ├── dto/                # request/ · response/
│   └── validator/
└── global/
    ├── config/             # JpaConfig, SecurityConfig, WebConfig
    ├── error/              # ErrorCode, BusinessException, GlobalExceptionHandler
    ├── response/           # ApiResponse, PageResponse
    ├── security/           # AuthUser, @CurrentUser
    ├── entity/             # BaseTimeEntity, BaseCreatedEntity
    └── audit/
```

---

## Coding Conventions

1. **삼항 연산자 금지** — `if/else` 또는 early return
2. **DTO 팩토리 메서드** — 서비스에서 `new XxxResponse(...)` 직접 생성 금지. `from()`/`of()` 정적 팩토리 사용
3. **Early return** — 조건이 맞지 않으면 일찍 반환해 중첩을 줄인다
4. **단일 책임 메서드** — 한 메서드는 한 가지 일만
5. **의미 있는 이름** — 축약어 없이 의도가 드러나게
6. **계층 경계** — Controller는 Repository를 직접 import하지 않는다 (Service 경유)
7. **`@Async` 메서드에 `@Transactional` 필수** — 새 스레드는 호출자 트랜잭션을 전파받지 못한다

### 컨트롤러 작성 패턴

```java
@GetMapping("/balance")
public ApiResponse<BalanceResponse> getBalance(@CurrentUser AuthUser authUser) {
    final BalanceResponse balance = balanceService.inquire(authUser.userId());
    return ApiResponse.success(balance, balance.toVoiceMessage());
}
```

- 현재 사용자는 **`@CurrentUser`로 받습니다.** `HttpServletRequest`에서 직접 꺼내거나 `userId`를 파라미터로 받지 않습니다.
- 응답은 **`ApiResponse`로 통일**합니다. 목록은 `PageResponse<T>`를 씁니다.
- 사용자에게 결과를 알려야 하면 `voiceMessage`를 채웁니다. **금액·숫자는 한국어 표기로 변환**하세요 — TTS가 `53000원`을 어떻게 읽을지 보장할 수 없습니다.
- 에러는 **던지기만** 합니다. `GlobalExceptionHandler`가 같은 형식으로 변환합니다.

```java
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
```

새 에러 코드가 필요하면 `ErrorCode`에 `message`와 `voiceMessage`를 **둘 다** 채우고 [docs/error-codes.md](docs/error-codes.md)를 갱신합니다.

---

## Testing

JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), H2 in-memory (`application-test.yml`).

- **DAMP > DRY** — `@BeforeEach`로 상태 공유 금지. 각 테스트를 독립적으로
- **결과를 검증한다** — `verify(...)` 같은 구현 호출이 아니라 상태 변화를 검증. 단 외부 호출·이벤트 발행처럼 side effect가 본질인 경우는 `verify` 사용 가능
- **AAA 패턴** — `// given / when / then` 주석으로 구분
- **메서드명은 한글 언더스코어** (`고위험_이체는_차단된다`), `@DisplayName`은 `<행위>하면 <결과>한다` 형식. "성공·실패·테스트" 접미사 금지
- **BDDMockito** — `given(...).willReturn(...)` (`when/thenReturn` 금지)
- **예외 테스트** — `assertThatThrownBy(...).isInstanceOf(BusinessException.class)`

### 이 프로젝트에서 반드시 테스트할 것

일반 CRUD보다 우선합니다. 돈이 움직이거나 사용자가 화면을 못 보는 지점입니다.

- 멱등성 — 같은 키로 두 번 요청하면 이체가 1건만 생성되는가
- FDS 분기 — LOW/MEDIUM/HIGH 각각의 이체 상태와 알림 발송
- FDS 호출 실패 — 평가를 못 받았을 때 이체가 통과되지 않는가
- 슬롯 만료 — 만료된 슬롯이 다음 발화에 섞이지 않는가
- PIN 잠금 — 잠금 상태에서 올바른 PIN을 넣어도 거부되는가
- 상태 전이 — `COMPLETED` 이후 다른 상태로 바뀌지 않는가

---

## Git

### 이슈부터 판다

**작업은 GitHub 이슈 생성으로 시작합니다.** 코드를 먼저 건드리지 않습니다.

```text
이슈 생성 → develop에서 브랜치 → 작업 → PR(develop 대상) → 리뷰 → 머지 → 이슈 close
```

```bash
gh issue create --title "feat: 잔액조회 API" --body "..."   # 1. 이슈
git checkout develop && git pull
git checkout -b feat/12-balance-api                          # 2. 이슈 번호 접두
# 3. 작업 후 PR 본문에 "Closes #12"
gh issue close <이슈번호> --comment "PR #<PR번호> 머지로 완료"   # 4. 머지 후 직접 닫는다
```

**`Closes #12`로는 자동으로 닫히지 않습니다.** GitHub의 자동 종료는 기본 브랜치(`main`)로 머지될 때만 동작하는데 기능 PR은 전부 `develop`을 대상으로 합니다. `Closes`는 PR과 이슈를 서로 연결해 두는 용도로만 쓰고, 이슈는 머지 후 직접 닫습니다.

이슈 본문에는 **무엇을·왜·완료 조건**을 적습니다. 완료 조건은 "무엇이 되면 done"인지 검증 가능한 기준이어야 합니다.

```text
main     — 배포 가능 상태
develop  — 통합 브랜치
feat/*   — 기능 개발 (fix/, docs/, refactor/, chore/ 도 동일)
```

- PR은 `develop` 대상으로 올립니다. `main`에 직접 커밋하지 않습니다.
- 커밋 메시지는 한국어로, 제목은 `<type>: <요약>` 형식입니다. 본문에는 **무엇을 했는지보다 왜 그렇게 했는지**를 씁니다.
- 커밋 전 `git status`로 포함될 파일을 확인합니다. 설정 파일이나 시크릿이 섞이지 않았는지 봅니다.

### AI가 작성했다는 표시를 남기지 않는다

커밋의 저자는 사람입니다. 아래 형태는 **모두 금지**입니다.

```text
Co-Authored-By: Claude <noreply@anthropic.com>
Co-Authored-By: Codex <codex@openai.com>
🤖 Generated with Claude Code
```

커밋 메시지뿐 아니라 **PR 본문·이슈·코드 주석에도** 남기지 않습니다.
도구가 트레일러를 자동으로 붙이도록 설정돼 있다면 커밋 전에 제거하세요.

---

## Security

- **`*.yml`은 전부 gitignore 대상입니다.** DB 비밀번호·API 키를 파일에 직접 적기 때문입니다. `.example` 템플릿은 쓰지 않으며, 필요한 값은 팀 채널(Notion/카톡)에서 공유합니다.
- 설정 항목을 추가·변경했다면 팀 채널에도 함께 공유합니다. 안 그러면 다른 팀원이 기동에 실패합니다.
- **Java 코드에 인증정보를 하드코딩하지 않습니다.** `@Value` / `@ConfigurationProperties`로 주입받습니다.
- AES 암호화 대상: `users.phone`, `transfers.to_account_num`, `guardian_links.guardian_phone`, 모든 토큰
- **`movi.auth.dev-mode`는 인증 필터 구현 전 개발용 장치입니다.** 운영 환경에서는 반드시 `false`여야 합니다.
