# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **파트 간 통합 계약은 [docs/integration-spec.md](docs/integration-spec.md)가 최우선 기준입니다.** 다른 문서와 충돌하면 이 문서를 따르세요. AI 내부 API 규격은 [docs/ai-api-contract.md](docs/ai-api-contract.md), 일자별 실행계획은 [docs/execution-plan.md](docs/execution-plan.md)에 있습니다.
>
> 도구 무관 공통 규약은 [AGENTS.md](AGENTS.md)에 있습니다. 특히 **이미 만들어진 공용 자산(엔티티 20개·에러코드·공통 응답·인증 컨텍스트)을 중복 생성하지 않도록** 해당 절을 먼저 확인하세요.
>
> 도메인별 상세 로직·불변식·테스트 작성 규칙은 [docs/domain-guide.md](docs/domain-guide.md)를 먼저 확인하세요.
>
> 데이터 모델은 [docs/ERD.md](docs/ERD.md), DDL은 [docs/schema.sql](docs/schema.sql), 개발 일정과 담당 배분은 [docs/schedule-backend.md](docs/schedule-backend.md)를 참조하세요.

## Project Overview

Movi는 시각장애인·시니어가 **화면 없이 음성만으로** 은행 업무를 볼 수 있게 하는 Voice-First 뱅킹 플랫폼입니다.
오픈뱅킹 API로 잔액조회·이체를 처리하고, Isolation Forest 기반 FDS로 이상 거래를 탐지해 보호자에게 알립니다.

시중 서비스 대부분이 "터치 앱 + 음성 부가기능"인 것과 달리, 이 프로젝트는 **화면 조작 없이 완결되는 흐름**을 목표로 합니다. 접근성은 부가 기능이 아니라 제품의 존재 이유입니다.

**Tech Stack**: Java 21, Spring Boot 4.1.0, Gradle 9.5.1, MySQL 8.0

**팀 구성**: 백엔드 3인(Jun / jjh / HANEUL MUN) · 프론트 · AI 파트 별도

## Development Commands

```bash
./gradlew build                                           # 전체 빌드
./gradlew bootRun --args='--spring.profiles.active=local' # 로컬 실행
./gradlew test                                            # 전체 테스트
./gradlew test --tests "TransferServiceTest"              # 단일 테스트
./gradlew clean build                                     # 클린 빌드
```

### 최초 세팅

`*.yml`은 gitignore 대상이라 clone 직후에는 설정 파일이 없습니다. **`.example` 템플릿은 쓰지 않습니다** — `application.yml`, `application-local.yml`, `application-test.yml`을 직접 만들고, 채워야 할 값(DB 비밀번호·API 키 등)은 팀 채널(Notion/카톡)에서 확인하세요.

그다음 `application-local.yml`의 `password`를 본인 로컬 MySQL 비밀번호로 채우고, DB를 준비합니다.

```bash
mysql -u root -p -e "CREATE DATABASE movi CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p movi < docs/schema.sql
```

## Application Architecture

**단일 모듈** 구조입니다. 진입점은 `com.movi_backend.MoviBackendApplication`.

### DDD Layered Structure

```text
com.movi_backend/
├── domain/{도메인}/
│   ├── controller/         # REST 엔드포인트
│   ├── application/        # 비즈니스 로직 (*Service, *Facade)
│   ├── repository/
│   ├── entity/             # JPA 엔티티
│   ├── dto/                # request/ · response/
│   └── validator/
└── global/
    ├── config/
    ├── error/              # 공통 예외·에러코드
    ├── security/           # JWT, 인증 필터
    └── util/               # 암호화, 마스킹
```

### 도메인 구성

| 도메인 | 책임 | 주요 테이블 |
|---|---|---|
| `auth` | 카카오 로그인, PIN·생체인증, JWT | `users`, `oauth_accounts`, `user_credentials`, `devices` |
| `account` | 오픈뱅킹 연동, 계좌 관리, 잔액 | `openbanking_connections`, `accounts`, `balance_snapshots` |
| `transfer` | 이체 실행, 상태 관리, 거래내역 | `transfers`, `transactions`, `transfer_recipients` |
| `voice` | 음성 세션, 명령 기록, 슬롯 필링 | `voice_sessions`, `voice_commands` |
| `fds` | 위험도 평가, 룰 관리 | `fds_assessments`, `fds_rules`, `user_transfer_profiles` |
| `guardian` | 보호자 연결, 알림 발송 | `guardian_links`, `notifications` |

### 도메인 설명 문서 — `domain-note.md`

각 도메인 패키지 루트(`domain/{도메인}/domain-note.md`)에 그 도메인을 설명하는 문서를 둡니다.

- **내용**: 해당 도메인의 책임·주요 클래스와 흐름·지켜야 할 불변식·규칙 요약 + 주요 변경 이력(무엇을 왜 바꿨는지, 시간순)
- **갱신 시점**: 새로 쓰는 문서가 아니라, 도메인 구조나 규칙이 실제로 바뀌어 문서가 현재 코드와 어긋나게 될 때마다 갱신합니다. 코드만 건드리고 구조·규칙에 변화가 없으면 갱신하지 않아도 됩니다
- 도메인 전반의 불변식·설계 계약은 [docs/domain-guide.md](docs/domain-guide.md)에 정리되어 있으니, `domain-note.md`는 이를 대체하지 않고 그 도메인 패키지 내부 관점의 보충 설명으로 씁니다

## 도메인 규칙 (반드시 지킬 것)

이 프로젝트는 **돈이 움직이고**, **사용자가 화면을 볼 수 없습니다**. 아래는 협상 대상이 아닙니다.

### 1. AI 추출 결과를 신뢰하지 않는다

음성 명령의 intent·entity는 AI 파트가 추출하지만, **필수값 검증과 실행 판단은 백엔드가 최종 책임**집니다.
AI가 금액을 환각으로 채워 넣거나 놓쳐도 이체 API에서 막혀야 합니다.

- 엔티티 누락 판정, 재질문 여부, 대화 상태 유지는 백엔드가 수행
- 재질문 문구는 **템플릿 고정** — 매번 달라지면 시각장애인 사용자가 혼란스러움
- `voice_sessions`의 슬롯은 만료 시간을 두고 관리 (오래된 슬롯이 살아 있으면 엉뚱한 이체가 나감)

### 2. 이체는 멱등성이 필수

음성은 오인식·중복 발화가 잦습니다. 모든 이체 요청은 `idempotency_key`로 중복을 차단합니다.

### 3. FDS 분기는 세 갈래

```text
LOW    → ALLOW             → 이체 완료
MEDIUM → ALLOW_WITH_ALERT  → 이체 완료 + 보호자 SMS 통보
HIGH   → BLOCK             → 이체 차단 + 보호자 SMS 통보
```

보호자 *승인*(사전 차단) 기능은 MVP 범위에서 제외했습니다. 보호자는 승인 권한 없이 알림만 받습니다.

### 4. 민감정보는 암호화·마스킹

- **AES 암호화 대상**: `users.phone`, `accounts.account_num_masked`, `transfers.to_account_num`, `guardian_links.guardian_phone`, 모든 `access_token`·`refresh_token`
- **로그 금지**: 계좌번호·전화번호·인증 토큰은 로그에 원문으로 남기지 않는다

### 5. 오류도 음성으로 안내된다

예외 메시지는 화면에 찍히고 끝나는 게 아니라 **TTS로 읽힙니다**. 스택 트레이스나 영문 기술 용어가 그대로 나가면 안 됩니다.

`ErrorCode`는 `message`(화면·로그용)와 `voiceMessage`(TTS용)를 분리해 갖습니다. 새 에러 코드를 추가할 때 **둘 다 채우고**, [docs/error-codes.md](docs/error-codes.md)를 함께 갱신하세요.

```java
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
```

### 6. 응답은 `ApiResponse`로 통일한다

성공·실패 응답의 구조가 같습니다. 컨트롤러는 `ApiResponse.success(...)`를 반환하고, 에러는 던지기만 하면 `GlobalExceptionHandler`가 같은 형식으로 변환합니다. 목록은 `PageResponse<T>`를 씁니다.

사용자에게 결과를 알려야 하는 응답에는 `voiceMessage`를 채웁니다. **금액·숫자는 한국어 표기로 변환해서 넣으세요** — TTS가 `53000원`을 어떻게 읽을지 보장할 수 없습니다.

```java
return ApiResponse.success(balance, "국민은행 통장에 5만 3천원 있어요.");
```

자세한 규약은 [docs/api-response.md](docs/api-response.md) 참조.

## 외부 연동

| 대상 | 용도 | 비고 |
|---|---|---|
| 오픈뱅킹 API | 계좌 조회·잔액·이체 | Sandbox 사용. 승인 지연 대비 **Mock 어댑터 우선 구현** |
| Google Cloud STT/TTS | 음성 인식·합성 | AI 파트 담당 |
| FDS 서비스 | 위험도 평가 | AI 파트 제공. 요청/응답 스키마를 문서로 합의 후 Mock으로 선개발 |
| SMS | 보호자 알림 | Twilio는 국내 발신 제약 있음 — 국내 서비스(NHN Toast, 알리고) 대안 검토 |

외부 인증정보는 `application-local.yml`에 직접 적습니다. **이 파일들(`*.yml`)은 gitignore 대상이라 커밋되지 않습니다.** `.example` 템플릿은 쓰지 않으며, 필요한 값은 팀 채널(Notion/카톡)에서 공유합니다.

**Java 코드에는 어떤 인증정보도 하드코딩하지 않습니다.** `@Value`나 `@ConfigurationProperties`로 설정에서 주입받으세요.

## 코드 작성 원칙

1. **삼항 연산자 금지** — 조건 분기는 `if/else` 또는 early return으로 작성한다
2. **DTO 팩토리 메서드** — 서비스에서 `new DTO(...)` 직접 생성 금지. DTO의 `from()`/`of()` 정적 팩토리를 호출한다
3. **Early return** — 조건이 맞지 않으면 일찍 반환해 중첩을 줄인다
4. **단일 책임 메서드** — 한 메서드는 한 가지 일만 한다
5. **의미 있는 이름** — 축약어 없이 의도가 드러나는 이름을 쓴다
6. **계층 경계** — Controller는 Repository를 직접 import하지 않는다 (Service 경유)
7. **`@Async` 메서드에 `@Transactional` 필수** — 알림 발송 등 비동기 처리 시 트랜잭션 누락 주의

## 작업 순서

1. 관련 문서 확인 — [docs/ERD.md](docs/ERD.md)에서 해당 도메인의 테이블·관계 파악
2. 변경 계획과 완료 조건 작성 — 어떤 파일을 왜 고치는지, "무엇이 되면 done"인지 검증 가능한 기준으로
3. 코드 수정
4. 도메인 구조·규칙이 바뀌었다면 해당 패키지의 `domain-note.md` 갱신 (신규 도메인이면 새로 작성)
5. 테스트 — `./gradlew test`
6. 스키마 변경 시 [docs/schema.sql](docs/schema.sql)과 [docs/ERD.md](docs/ERD.md)를 함께 갱신 (ERDCloud 임포트용 SQL도 같이)

## 작업 흐름 — 이슈부터 판다

**모든 작업은 GitHub 이슈 생성으로 시작합니다.** 브랜치를 먼저 만들지 않습니다.

```text
이슈 생성 → develop에서 브랜치 → 작업 → PR(develop 대상) → 리뷰 → 머지 → 이슈 close
```

1. **이슈 생성** — 무엇을·왜·완료 조건을 적습니다. 완료 조건은 검증 가능해야 합니다
   ```bash
   gh issue create --title "feat: 잔액조회 API" --body "..."
   ```
2. **브랜치 생성** — 이슈 번호를 접두로 붙여 추적이 되게 합니다
   ```bash
   git checkout develop && git pull
   git checkout -b feat/12-balance-api      # 12 = 이슈 번호
   ```
3. **PR 본문에 이슈 연결** — `Closes #12`를 적으면 머지 시 이슈가 자동으로 닫힙니다

```text
main     — 배포 가능 상태
develop  — 통합 브랜치. 기능 브랜치는 여기서 따고 여기로 병합한다
feat/*   — 기능 개발 (fix/, docs/, refactor/, chore/ 도 동일)
```

`main`에 직접 커밋하지 않습니다.

### 커밋 메시지

- 한국어로 쓰고, 제목은 `<type>: <요약>` 형식입니다
- 본문에는 **무엇을 했는지보다 왜 그렇게 했는지**를 씁니다
- **AI가 작성했다는 표시를 남기지 않습니다.** 커밋의 저자는 사람입니다

  아래 형태는 모두 금지입니다.

  ```text
  Co-Authored-By: Claude <noreply@anthropic.com>
  Co-Authored-By: Codex <codex@openai.com>
  🤖 Generated with Claude Code
  ```

  PR 본문·이슈·코드 주석에도 마찬가지로 남기지 않습니다.
- 커밋 전 `git status`로 포함될 파일을 확인합니다. 설정 파일이나 시크릿이 섞이지 않았는지 봅니다
