# 도메인 가이드

Movi 백엔드의 도메인 지도·불변식·코딩 주의사항입니다.
프로젝트 개요·기술 스택·공통 코드 원칙은 루트 [CLAUDE.md](../CLAUDE.md)를 참조하세요.
데이터 모델은 [ERD.md](ERD.md), DDL은 [schema.sql](schema.sql)에 있습니다.

> **현재 상태**: 프로젝트 초기 단계로 대부분의 도메인 코드는 아직 작성 전입니다.
> 이 문서는 "구현된 것의 설명"이 아니라 **구현 시 지켜야 할 설계 계약**입니다.
> 클래스명은 컨벤션 제안이며, 실제 구현과 달라지면 이 문서를 갱신하세요.

## Core Domain Modules

`com.movi_backend.global`은 공통 인프라 (`config`, `security`, `error`, `filter`, `util`, `properties`). 도메인 패키지는 아래.

### `auth` — 인증

- 최초 계정 연결은 카카오 OAuth로 처리하고, 이후에는 전화번호 + PIN으로도 로그인할 수 있다
- 카카오 로그인 후 PIN을 최초 등록하며, 두 로그인 방식 모두 자체 Access/Refresh JWT를 발급한다
- 로그아웃 시 `users.token_version`을 증가시켜 기존 Access/Refresh JWT를 즉시 무효화한다
- 토큰: Access(단기, `Bearer`) / Refresh
- **불변식**
  - `oauth_accounts (provider, provider_user_id)` UNIQUE — 같은 카카오 계정으로 중복 가입 불가
  - `user_credentials.user_id` UNIQUE — 회원 1명당 PIN 1개
  - PIN 연속 실패 시 `failed_attempts` 증가, 임계치 초과 시 `locked_until` 설정. **잠금 중에는 검증 자체를 건너뛰고 즉시 거부한다**
- `devices.is_trusted`는 FDS 피처로 쓰이므로, 신규 기기 로그인 시 반드시 기록

#### 인증 컨텍스트 — `@CurrentUser`

컨트롤러에서 현재 사용자는 `@CurrentUser`로 받는다. 각자 `HttpServletRequest`에서 꺼내거나 파라미터로 `userId`를 받지 않는다.

```java
@GetMapping("/accounts")
public ApiResponse<List<AccountResponse>> getAccounts(@CurrentUser AuthUser authUser) {
    return ApiResponse.success(accountService.findAll(authUser.userId()));
}
```

**JWT 구현 전에도 개발할 수 있다.** `movi.auth.dev-mode=true`이면 인증 없이 헤더로 사용자를 지정한다.

```bash
curl -H "X-Dev-User-Id: 3" http://localhost:8080/api/accounts
```

헤더가 없으면 `movi.auth.dev-user-id`(기본 1)를 쓴다. 인증 정보가 있으면 항상 그쪽이 우선한다.

> **인증 담당자에게** — JWT 필터에서 `AuthUser`를 `Authentication`의 principal로 넣으면 리졸버가 그대로 동작한다. 별도 수정이 필요 없다. 필터가 완성되면 `dev-mode`를 끄고, **운영 환경에서는 반드시 false여야 한다.**

### `account` — 계좌·오픈뱅킹

- 오픈뱅킹 API 연동으로 계좌 등록·잔액 조회
- **불변식**
  - `accounts.fintech_use_num` UNIQUE — 핀테크이용번호는 계좌의 실질 식별자
  - `openbanking_connections.user_seq_no` UNIQUE — 금결원 사용자일련번호
  - 사용자당 `is_primary=true` 계좌는 **최대 1개**. 기본 계좌 변경 시 기존 것을 먼저 해제한다
  - 토큰 만료(`expires_at`) 확인 후 갱신 — 만료된 토큰으로 호출하면 오픈뱅킹이 거부한다
- **Mock 어댑터 우선** — 오픈뱅킹 Sandbox 승인은 외부 변수다. 인터페이스를 먼저 정의하고 Mock 구현체로 개발한 뒤 실 API로 교체한다

- **오픈뱅킹 Port는 두 개다.** 역할이 겹치지 않으니 용도에 맞는 쪽을 쓴다

  | Port | 담당 | Mock 어댑터 | 전환 방식 |
  |---|---|---|---|
  | `BalanceInquiryPort` | 잔액조회 | `MockBalanceInquiryAdapter` | `@Profile("local","test")` |
  | `OpenBankingClient` | 계좌 목록·이체 | `MockOpenBankingClient` | `movi.openbanking.mode` |

  잔액은 호출 빈도와 캐시 정책(`balance_snapshots`)이 달라 분리했다. **새 오퍼레이션을 추가할 때 어느 Port에 넣을지 먼저 정하고, 같은 기능을 양쪽에 만들지 않는다.**

  `MockOpenBankingClient`는 잔액을 상태로 들고 이체할 때 차감한다. 고정값이면 잔액 부족 분기를 시연할 수 없기 때문이다. 같은 `tranId`로 재호출하면 새 이체를 만들지 않고 기존 결과를 돌려줘, 실제 오픈뱅킹의 멱등 동작을 Mock 단계에서 검증할 수 있다
- `balance_snapshots`는 API 호출 비용 절감 + FDS 피처(잔액 대비 이체 비율)용. 조회할 때마다 남긴다

### `transfer` — 이체·거래내역

- **상태 전이** (역방향 금지)
  ```text
  PENDING → RISK_REVIEW → COMPLETED
                        → BLOCKED
            → FAILED / CANCELED
  ```
- **불변식**
  - `transfers.idempotency_key` UNIQUE — 음성은 오인식·중복 발화가 잦다. 클라이언트 발급 키로 중복 이체를 차단한다
  - `COMPLETED` 이후에는 어떤 상태로도 전이하지 않는다
  - 모든 이체는 FDS 평가를 거친다. 평가 없이 `COMPLETED`가 될 수 없다
- `transfer_recipients (user_id, nickname)` UNIQUE — "엄마"가 두 명일 수 없다. 음성 별칭이 곧 조회 키
- `transfer_recipients.transfer_count`는 FDS의 "처음 보내는 상대" 피처. 이체 성공 시 증가시킨다

### `voice` — 음성 세션·명령

- STT/NLU는 AI 파트 담당. 백엔드는 **추출 결과를 받아 검증하고 실행을 판단**한다
- **불변식**
  - AI가 넘긴 엔티티는 신뢰하지 않는다. 필수값 검증은 백엔드가 최종 책임진다
  - 필수 슬롯 누락 시 `voice_commands.status = CLARIFY`로 기록하고 재질문한다
  - 재질문 문구는 **템플릿 고정**. 매번 달라지면 시각장애인 사용자가 혼란스럽다
- **슬롯 세션 만료가 핵심**
  "엄마한테" 발화 후 3분 뒤 다른 말을 했을 때 이전 슬롯이 살아 있으면 엉뚱한 이체가 나간다.
  `VoiceSession`이 슬롯의 **단일 소유자**이며 저장·병합·만료를 모두 책임진다. 프론트와 AI는 슬롯을 보관하지 않는다.

  | 항목 | 값 | 상수 |
  |---|---:|---|
  | 일반 세션 유효시간 | 5분 | `SESSION_TIMEOUT_MINUTES` |
  | 재질문·확인 대기 | 60초 | `PENDING_TIMEOUT_SECONDS` |
  | 같은 슬롯 재질문 | 3회 | `MAX_RETRY_COUNT` |

  만료 시 **슬롯을 전부 폐기한다. 일부만 살리지 않는다.** 3회를 넘기면 세션을 종료하고 `VOICE_4006`을 반환한다.

- **세션 상태 전이** (`VoiceSessionStatus`)
  ```text
  ACTIVE ─┬─ CLARIFYING
          ├─ AWAITING_CONFIRMATION ─┬─ PROCESSING → COMPLETED
          │                         └─ CANCELED
          └─ EXPIRED
  ```
  `PROCESSING` 중에는 확인 발화를 다시 받지 않는다 — 중복 이체를 막는 1차 방어선이다.

- **확인 정보 불변성** — `AWAITING_CONFIRMATION` 이후 금액·출금계좌·수취인이 바뀌면 기존 `confirmationId`와 `idempotencyKey`를 폐기하고 확인 문장을 새로 만든다
- `stt_confidence`는 FDS 피처로 전달된다 — 인식 신뢰도가 낮은 이체는 위험 신호
- **신뢰도 기준** — 구간은 서로 겹치지 않는다

  | 구간 | 처리 |
  |---|---|
  | `0.80` 이상 | 다음 백엔드 검증으로 진행 |
  | `0.60` 이상 `0.80` 미만 | 자동 실행하지 않고 전체 발화 재요청 |
  | `0.60` 미만 또는 null | `VOICE_4004`로 재발화 요청 |

  `sttConfidence`와 `nluConfidence` 중 하나라도 기준 미만이면 더 낮은 구간을 따른다.
  개별 슬롯 confidence가 `0.80` 미만이면 그 슬롯을 누락으로 처리한다.

### `fds` — 이상거래 탐지

- Isolation Forest 기반. 모델·추론 API는 AI 파트 제공, 백엔드는 호출과 판정 반영을 담당
- **분기**
  ```text
  LOW    → ALLOW             → 이체 완료
  MEDIUM → ALLOW_WITH_ALERT  → 이체 완료 + 보호자 SMS 통보
  HIGH   → BLOCK             → 이체 차단 + 보호자 SMS 통보
  ```
- **불변식**
  - `fds_assessments.transfer_id` UNIQUE — 이체 1건당 평가 1건
  - `features` JSON에 모델 입력 스냅샷을 남긴다. 모델 교체 후 재평가·백테스트에 필요하다.
    MVP에서는 `policyVersion`, `ruleScore`, `finalRiskScore`, `reasonCodes`도 이 JSON에 함께 담는다 (전용 컬럼은 운영 분석 요구가 생기면 별도 스키마 변경으로 추가)
  - FDS 호출 실패 시 **이체를 통과시키지 않는다**. 평가 불가는 곧 위험이다.
    타임아웃·통신 오류·역직렬화 실패·필수값 누락·정의되지 않은 위험도/결정 조합은 전부 평가 실패로 본다
  - **임계값을 백엔드가 재계산하지 않는다.** AI가 반환한 위험도와 결정 조합만 검증한다
  - 타임아웃은 **3초, 자동 재시도 없음**

- **Cold start** — 최근 30일 성공 이체가 3건 미만이면 `coldStart=true`로 전달한다. 프로필이 비어 있으면 이상치 판정이 무의미하므로 AI가 룰 정책으로 최소 위험도를 상향한다

- **이체 한도** (설정값으로 관리, 하드코딩 금지)

  | 항목 | 값 |
  |---|---:|
  | 최소 금액 | 1원 |
  | 1회 한도 | 1,000,000원 |
  | 1일 누적 한도 | 3,000,000원 |

  오픈뱅킹 한도가 더 낮으면 더 낮은 값을 적용한다.
- `user_transfer_profiles`는 배치로 갱신. 프로필이 비어 있으면 이상치 판정이 무의미하므로, 신규 사용자는 별도 정책이 필요하다

### `guardian` — 보호자·알림

- 보호자 연결(SMS 초대 → 수락) + 이상거래 통보
- **불변식**
  - 보호자는 **승인 권한이 없다**. MVP에서 사전 차단 기능은 제외했고 알림만 받는다
  - `guardian_links.guardian_user_id`는 nullable — SMS 초대 시점에 보호자는 아직 미가입 상태다. 수락 시 바인딩한다
  - `guardian_links.invite_token` UNIQUE + 만료 시각 필수
  - `notifications.transfer_id`로 "어떤 이체 때문에 나간 알림인지" 추적 가능해야 한다. 승인이 없는 만큼 알림이 유일한 대응 수단이다
- 알림 발송은 외부 API 실패가 잦다. `status`(QUEUED/SENT/FAILED)와 `provider_msg_id`를 남긴다

## 설정 · 외부 연동

- **설정 파일은 `.yml` 확장자를 쓴다.** `application.yml`(공통) / `application-local.yml`(로컬 MySQL) / `application-test.yml`(H2). 기본 프로파일은 `local`
- **`*.yml`은 전부 gitignore 대상이다.** 인증정보를 파일에 직접 적기 때문이다. 팀 공유는 `*.yml.example` 템플릿으로 하며, 템플릿에는 플레이스홀더만 둔다
  - clone 직후에는 설정 파일이 없다. `.example`을 복사해서 만들어야 기동된다
  - 설정 항목을 추가·변경했다면 **`.example`도 함께 갱신**한다. 안 그러면 다른 팀원이 기동에 실패한다
  - Java 코드에는 인증정보를 하드코딩하지 않는다. `@Value` / `@ConfigurationProperties`로 주입받는다
- **`ddl-auto`**: local은 `validate`, test는 `create-drop`.
  엔티티를 바꿨다면 [schema.sql](schema.sql)도 함께 고치고 DB에 반영해야 기동된다. `update`를 쓰지 않는 이유는 각자 엔티티를 고칠 때마다 로컬 DB가 조용히 달라져 "내 로컬에선 되는데"가 생기기 때문이다
- **외부 연동**: 오픈뱅킹 API, 카카오 OAuth, Google Cloud STT/TTS(AI 파트), FDS 추론 서비스(AI 파트), SMS
- **SMS 주의** — Twilio는 국내 발신 제약이 있다. 국내 서비스(NHN Toast, 알리고) 대안을 Week 1에 검증한다
- **엔티티**: `created_at`/`updated_at`을 가진 엔티티는 `BaseTimeEntity`를 상속한다 (`@EnableJpaAuditing`은 `JpaConfig`에 선언됨)
- **보안 설정**: 현재 `SecurityConfig`는 모든 요청을 `permitAll`로 열어 둔 상태다. 의존성만 넣고 설정을 두지 않으면 전 엔드포인트가 기본 인증으로 잠겨 다른 파트가 막히기 때문이다. **인증 담당자가 JWT 필터와 함께 실제 인가 규칙으로 교체하며, 그 전까지 배포하지 않는다**

## 백엔드 코딩 주의사항

- **Swagger**: `controller/docs/`에 별도 `Docs` 인터페이스로 문서화 — 컨트롤러 본문은 깔끔하게 유지한다
- **상태값은 `VARCHAR` + Java enum + `@Enumerated(EnumType.STRING)`**
  DB ENUM 타입은 쓰지 않는다. `ddl-auto: update`가 기존 ENUM 컬럼에 새 값을 추가하지 못해 매번 수동 `ALTER`가 필요해지기 때문이다. `@Enumerated(EnumType.ORDINAL)`은 순서가 바뀌면 데이터가 깨지므로 금지
- **`@Async` 비동기** (알림 발송에 사용)
  - `@Async` 메서드는 **별도 빈**에 배치한다 (self-invocation 시 프록시가 우회돼 동기 실행됨)
  - 새 스레드는 호출자 트랜잭션을 전파받지 못하므로, DB 작업이 있는 `@Async` 메서드에는 `@Transactional`을 직접 선언한다
- **민감정보 처리**
  - AES 암호화: `users.phone`, `transfers.to_account_num`, `guardian_links.guardian_phone`, 모든 토큰
  - 로그 마스킹: 계좌번호·전화번호·인증 토큰은 원문으로 남기지 않는다. `toString()`에도 포함시키지 않는다
- **예외 메시지는 TTS로 읽힌다**
  스택 트레이스나 영문 기술 용어가 사용자에게 그대로 전달되면 안 된다. 에러 코드마다 사용자가 들었을 때 이해할 수 있는 한국어 안내 문구를 함께 정의한다

## 테스트 작성 규칙

**프레임워크**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), H2 in-memory (`application-test.yml`).

1. **DAMP > DRY** — `@BeforeEach`로 상태 공유 금지. 반복 객체 생성은 Fixture 클래스로 분리해 각 테스트를 독립적으로 유지한다
2. **결과를 검증한다** — `verify(...)` 같은 구현 호출이 아니라 상태 변화를 검증한다 (`assertEquals(TransferStatus.BLOCKED, transfer.getStatus())`)
3. **AAA 패턴** — `// given / when / then` 주석으로 구분한다
4. **명세에 비즈니스 행위를 담는다** — 메서드명은 한글 언더스코어(`고위험_이체는_차단된다`), `@DisplayName`은 `<행위>하면 <결과>한다/반환한다/예외가 발생한다` 형식. "성공·실패·테스트" 접미사 금지
5. **BDDMockito** — `given(...).willReturn(...)` 사용 (`when/thenReturn` 금지)
6. **예외 테스트** — `assertThatThrownBy(() -> ...).isInstanceOf(BusinessException.class)`

**테스트 구분**: Unit(도메인 모델·비즈니스 로직) / Integration(주요 흐름·DB 등 외부 의존성) / E2E(사용자 흐름 전체).

### 이 프로젝트에서 반드시 테스트할 것

일반적인 CRUD보다 우선순위가 높습니다. 돈이 움직이거나 사용자가 화면을 볼 수 없는 지점입니다.

- **멱등성** — 같은 `idempotency_key`로 두 번 요청하면 이체가 1건만 생성되는가
- **FDS 분기** — LOW/MEDIUM/HIGH 각각에서 이체 상태와 알림 발송이 명세대로인가
- **FDS 호출 실패** — 평가를 못 받았을 때 이체가 통과되지 않는가
- **슬롯 만료** — 만료된 슬롯이 다음 발화에 섞이지 않는가
- **PIN 잠금** — 잠금 상태에서 올바른 PIN을 넣어도 거부되는가
- **상태 전이** — `COMPLETED` 이후 다른 상태로 바뀌지 않는가
