# Movi Backend

> 시각장애인과 시니어가 화면을 보지 않고도 음성으로 잔액을 확인하고 안전하게 송금할 수 있도록 돕는 Voice-First 금융 백엔드

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Build](https://github.com/movi-ai-challenge/movi_backend/actions/workflows/deploy-develop.yml/badge.svg?branch=develop)

Movi는 음성 인식 결과를 곧바로 금융 실행으로 연결하지 않습니다. 백엔드가 금액·수취인·계좌 소유권·한도·잔액을 다시 검증하고, 모든 송금에 FDS 평가와 멱등성 검사를 적용한 뒤에만 이체를 실행합니다.

문서 기준: **2026-08-28 · `develop`**

## 왜 이 프로젝트를 만들었나

일반적인 모바일 뱅킹은 화면 탐색과 정확한 터치를 전제로 합니다. 이는 시각장애인과 디지털 환경에 익숙하지 않은 시니어에게 높은 진입 장벽이 됩니다. 음성 인터페이스는 이 장벽을 낮출 수 있지만, 금융에서는 오인식 한 번이 실제 자금 이동으로 이어질 수 있다는 더 큰 위험이 생깁니다.

Movi Backend는 이 문제를 다음 원칙으로 해결합니다.

- **AI는 해석하고, 백엔드는 검증하고 결정합니다.**
- **확인되지 않은 정보로 송금하지 않습니다.**
- **위험도를 평가하지 못하면 송금을 통과시키지 않습니다.**
- **같은 요청이 반복돼도 실제 이체는 한 번만 실행합니다.**
- **화면을 보지 않아도 결과를 이해할 수 있도록 모든 금융 응답에 음성 안내 문구를 제공합니다.**

## 핵심 사용자 흐름

```text
로그인 (카카오 또는 PIN)
  → 보호자 등록 (전화번호)
  → 음성 세션 시작
  → 음성 업로드
  → AI Voice API에서 STT·Intent·Entity 분석
  → 백엔드에서 신뢰도·권한·금액·수취인 재검증
  → 누락 정보 재질문 또는 최종 확인
  → FDS 위험도 평가
  → LOW/MEDIUM 이체 실행 또는 HIGH 차단
  → MEDIUM/HIGH면 등록된 보호자 번호로 경고 문자 발송
  → 거래·평가·알림 상태 저장
  → 텍스트 + voiceMessage 응답
```

보호자를 등록하지 않으면 위험이 감지돼도 보낼 대상이 없어 문자가 나가지 않습니다.

## 이 프로젝트의 기술적 핵심

| 과제 | 구현한 해결 방식 |
|---|---|
| 음성 오인식이 바로 송금되는 위험 | 슬롯 검증, confidence 정책, 60초 만료, 최종 확인 상태를 백엔드가 소유 |
| 중복 발화·네트워크 재시도 | 사용자 잠금, 멱등성 키 조회, DB UNIQUE 제약으로 다중 방어 |
| FDS 장애 중 송금 통과 위험 | 타임아웃·통신 오류·잘못된 응답을 모두 Fail-Closed 처리 |
| 외부 API 성공과 내부 상태 불일치 | 송금 상태 머신, 은행 거래 시각·잔액 반영, 실패 상태 영속화 |
| 민감정보 유출 | AES-GCM 암호화, HMAC 검색 해시, 외부 호출 직전 최소 범위 복호화 |
| 알림 장애가 송금 트랜잭션을 되돌리는 문제 | 송금과 보호자 알림을 별도 트랜잭션으로 분리하고 지연 재시도 |
| 신규 사용자 FDS 데이터 부족 | 최근 30일 행동 프로필 배치와 cold-start 정책 적용 |

## 아키텍처

```text
┌──────────────┐       multipart audio       ┌──────────────────┐
│   Frontend   │ ──────────────────────────▶ │  Spring Backend  │
└──────────────┘                              │                  │
       ▲                                      │ 인증·세션·검증   │
       │ text + voiceMessage                  │ 금융 상태 소유    │
       └──────────────────────────────────────│                  │
                                              └───────┬──────────┘
                                                      │
                     ┌────────────────────────────────┼────────────────────────┐
                     │                                │                        │
                     ▼                                ▼                        ▼
             ┌──────────────┐                ┌──────────────┐        ┌────────────────┐
             │ AI Voice API │                │  AI FDS API  │        │ OpenBanking API│
             │ STT / NLU    │                │ 위험도 평가   │        │ 계좌·잔액·이체  │
             └──────────────┘                └──────────────┘        └────────────────┘
                                                      │
                                                      ▼
                                              ┌──────────────┐
                                              │ SMS Provider │
                                              │ 보호자 알림   │
                                              └──────────────┘
```

도메인 계층은 외부 연동 구현에 직접 의존하지 않습니다. Voice, FDS, 오픈뱅킹, SMS는 Port/Adapter 또는 Client 경계로 분리해 Mock과 실제 HTTP 구현을 설정으로 교체합니다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0, Spring Web MVC, Spring Security |
| Persistence | Spring Data JPA, MySQL 8.0, H2 |
| Build | Gradle 9.5.1 |
| Test | JUnit 5, Mockito, Spring Boot Test |
| Observability | Actuator, Prometheus |
| Delivery | Docker, GitHub Actions, Nginx, AWS EC2 |
| Security | JWT, BCrypt, AES-GCM, HMAC-SHA256 |

## 배포 주소

| 구분 | 주소 |
|---|---|
| API | `https://moviback.duckdns.org` |
| API 문서 | `https://moviback.duckdns.org/swagger-ui/index.html` |
| 헬스 체크 | `https://moviback.duckdns.org/actuator/health` |

`develop` 에 머지되면 GitHub Actions 가 자동 배포한다.

**CORS 허용 오리진은 `CorsProperties` 한 곳에서만 관리한다.** 프론트 주소가 늘면 이 클래스만 고치면 되고, 서버 설정은 건드리지 않는다. nginx 에도 CORS 를 두면 헤더가 두 번 나가 브라우저가 응답을 거부한다(이슈 #82).

## 프로젝트 전체 현황

> 갱신일: 2026-08-28 · 마감: 2026-08-31
>
> 백엔드 `develop@e0667fc` · 프런트 `main@7c94998` · AI `main@2026-08-26`

세 파트 모두 **각자의 기능은 구현이 끝났고, 서로 연결하는 일이 남았습니다.**

| 파트 | 구현 | 연동 | 지금 막고 있는 것 |
|---|---|---|---|
| 백엔드 | 완료 (293개 테스트 통과) | Mock 기준 완주 | AI staging URL 없음 |
| 프런트 | 완료 (PR 8개 대기) | 백엔드 계약 반영 완료 | **PR 8개 미병합** |
| AI | Voice·FDS 각각 동작 | 미연결 | 배포 주소·응답 계약 |

### 파트 간 의존 관계

```text
AI staging URL ─────────┐
                        ▼
              백엔드 실 연동(#104) ──┐
                                     ▼
프런트 PR 8개 병합 ──────────────► staging E2E ──► 시연
                        ▲
배포 서버에 시드 적용 ───┘
```

**AI 응답이 가장 상위 의존성입니다.** 나머지는 모두 팀 내부에서 처리할 수 있습니다.

### 남은 일과 담당

| 우선순위 | 할 일 | 담당 | 상태 |
|:---:|---|---|---|
| P0 | 프런트 PR 8개 병합 | 프런트 | 전부 `MERGEABLE`, 즉시 가능 |
| P0 | 배포 서버 yml에 `movi.seed.enabled: true` | 인프라 | 서버 접근 필요 |
| P0 | AI Voice·FDS staging URL과 계약 확정 | AI | [movi_ai#1](https://github.com/movi-ai-challenge/movi_ai/issues/1) 미응답 |
| P0 | `/api/openbanking/callback` 공개 경로 + 프런트 302 복귀 | 백엔드(계좌) | 미착수 — 프런트 연결 흐름이 막혀 있음 |
| P1 | AI 계약 확정 후 백엔드 실 연동 전환 | 백엔드 | [#104](https://github.com/movi-ai-challenge/movi_backend/issues/104) |
| P1 | 오픈뱅킹 Sandbox 실 이체 1건 종단 검증 | 백엔드(계좌) | Adapter는 구현 완료 |
| P1 | staging E2E (인증 → 조회 → 송금 → 보호자 알림) | 전원 | 위 P0가 선행 |
| P2 | 국내 SMS provider 연동 | 백엔드(알림) | 현재 Mock sender |
| P2 | 접근성 실측 (200% 확대·VoiceOver·TalkBack) | 프런트 | |

### 지금 당장 할 수 있는 것

AI 답변을 기다리지 않고 오늘 처리 가능한 항목입니다.

1. **프런트 PR 8개 병합** — 가장 큰 미반영 작업입니다. `#21 → #24·#25·#26 → #27 → #28 → #23` 순서를 권장합니다(#22는 #28이 대체하므로 함께 병합하지 않습니다)
2. **OpenBanking callback 공개 + 302** — 백엔드 한 파일 수정이면 프런트 계좌 연결 흐름이 열립니다
3. **배포 서버 시드 적용** — 이게 없으면 staging E2E와 시연이 시작되지 않습니다

### 대안 계획

8/30까지 AI staging이 준비되지 않으면 **백엔드 Mock 어댑터로 시연**하되 화면과 음성에 Sandbox·시연임을 표시합니다. 오픈뱅킹 승인이 늦어도 같은 방식입니다. 자세한 기준은 [docs/execution-plan.md](docs/execution-plan.md) 6절을 따릅니다.

---

## 백엔드 구현 진행 상황

상태 표기는 다음 기준을 사용합니다.

- ✅ 코드·자동 테스트 구현 완료
- 🧪 실제 외부 환경 통합 검증 필요
- ⏳ 후속 작업

| 영역 | 상태 | 현재 구현 |
|---|:---:|---|
| 인증 | ✅ | 카카오 OAuth, PIN 로그인·등록, Access/Refresh JWT, 갱신·로그아웃, 운영 JWT 필터 |
| 계좌 연결·관리 | 🧪 | 계좌 목록·기본 계좌·별칭 완료. **콜백이 아직 인증 필요 경로이고 JSON을 반환해 프런트로 복귀하지 못한다** |
| 잔액조회 | ✅ | 기본/별칭 계좌 조회, 실시간 재조회, BalanceSnapshot 저장, Mock/실 API Adapter, 음성 조회(BALANCE) |
| 음성 세션 | ✅ | 업로드 검증, 슬롯 저장·병합, 재질문, 확인·취소, 만료·재시도 제한 |
| 송금 | ✅ | 한도·잔액 검증, 상태 머신, 멱등성, 동시성 제어, 거래내역 저장 |
| FDS | ✅ | Mock/HTTP Client, 응답 검증, LOW/MEDIUM/HIGH 분기, 평가 스냅샷, 30일 프로필 배치 |
| 거래내역 | ✅ | 기간·입출금 유형·계좌 필터, 페이징 조회, 단건 상세, 음성 안내, 음성 조회(HISTORY) |
| 보호자 등록 | ✅ | 로그인 후 이름·전화번호·관계 등록, 즉시 연결, 본인·중복 번호 차단 |
| 보호자 위험 알림 | ✅ | 활성 보호자 조회, 알림 이력, 송금과 트랜잭션 분리, 최대 3회 재시도 |
| 민감정보 보호 | ✅ | 전화번호·토큰·수취 계좌번호 암호화, 로그·응답 마스킹 |
| AI Voice staging | 🧪 | HTTP Client 구현 완료, 실제 모바일 음성과 staging 계약 검증 필요 |
| AI FDS staging | 🧪 | HTTP Client 구현 완료, 실제 모델·정책 버전 및 오류 시나리오 검증 필요 |
| 오픈뱅킹 Sandbox | 🧪 | OAuth·계좌·잔액·이체 Adapter 구현 완료, 실제 테스트베드 종단 검증 필요 |
| 실제 SMS | 🧪 | 솔라피(Solapi) Adapter 구현 완료, 서버 IP에서 실발송·수신 확인. 배포 환경에 `provider: solapi` 적용 후 재확인 필요 |
| 배포 | 🧪 | Docker·GitHub Actions·Nginx·헬스체크·롤백 구현, 운영 시크릿과 서버 기동 검증 진행 중 |
| 시연 시드 | ✅ | `movi.seed.enabled=true`로 데모 사용자·계좌·수취인·보호자 생성. LOW/MEDIUM/HIGH 세 시나리오 재현 가능 |
| 전체 E2E | 🧪 | 12개 시나리오 Mock 기반 통과, 실제 외부 연동 포함 종단 검증 필요 |

## 도메인별 구현

### 인증과 인가

- 카카오 OAuth 로그인과 PIN 로그인
- PIN BCrypt 해시 저장, 실패 횟수와 잠금 정책
- Access/Refresh JWT 발급과 토큰 갱신
- 로그아웃 시 `token_version` 증가로 기존 토큰 무효화
- 운영 환경은 공개 경로 외 JWT 인증 필수
- 로컬 `dev-mode`에서만 `X-Dev-User-Id` 지원

### 계좌와 오픈뱅킹

- OAuth state 검증 후 연결 정보 저장
- 연결 계좌 목록·기본 계좌·음성 별칭 관리
- 기본 계좌 및 지정 별칭 계좌의 잔액조회
- 잔액조회 결과를 FDS 입력과 비용 절감을 위한 스냅샷으로 저장
- Mock/실제 OAuth·계좌·잔액·이체 Adapter 제공
- 오픈뱅킹 토큰을 암호화해 저장하고 외부 호출 직전에만 복호화

### 음성 세션

- WebM/WAV와 Safari/iOS MP4·M4A, 최대 5MB·15초 검증
- AI Voice 응답의 request/session ID, confidence, 필수 필드 검증
- 공개 Voice 응답의 transcript를 마스킹해 계좌번호·전화번호 원문 차단
- 최종 확인 발화의 `confirmationId`를 서버 세션 값과 대조
- 금액·수취인 누락 시 기존 슬롯과 후속 발화 병합
- 재질문·확인 대기 60초, 동일 슬롯 재질문 최대 3회
- 확인 정보가 달라지면 기존 확인 정보 폐기
- 직접 계좌번호를 말하는 송금은 거부하고 등록 수취인만 사용
- 거래내역·잔액 조회는 확인 단계 없이 응답하고, 기간은 AI가 준 값을 재검증해 사용
- 계좌 별칭은 송금과 같은 신뢰도 기준으로 검증 — 엉뚱한 계좌를 읽어 주면 정정할 수 없음
- 송금 슬롯을 채우는 중 다른 의도가 오면 기존 슬롯 폐기 후 명령 대기로 복귀

```text
ACTIVE
├─ CLARIFYING
├─ AWAITING_CONFIRMATION
│  ├─ PROCESSING → COMPLETED
│  └─ CANCELED
└─ EXPIRED
```

### 송금과 거래

- 최소 금액·1회 한도·일일 누적 한도를 설정으로 관리
- 확인 직전 잔액 재조회
- 사용자 행 비관적 잠금으로 동시 일일 한도 우회 방지
- `(user_id, idempotency_key)` UNIQUE와 선행 조회를 함께 사용
- 실제 오픈뱅킹 결과의 거래 시각·잔액을 내부 거래에 반영
- 수취 계좌번호는 외부 이체 요청 생성 시에만 복호화
- 이체 성공 후 수취인 누적 횟수와 출금 거래내역 갱신
- 거래내역 목록은 건수만, 상세는 금액·잔액·메모를 음성으로 안내
- 없는 거래와 남의 거래를 같은 응답으로 처리해 ID 훑기를 막음

```text
PENDING → RISK_REVIEW → COMPLETED
                      → BLOCKED
        → FAILED / CANCELED
```

### FDS 이상거래 탐지

- 연결 1초, 응답 3초 제한과 자동 재시도 금지
- request ID, 모델·정책 버전, 점수 범위, risk/decision 조합 검증
- 소켓 타임아웃과 HTTP 504를 `ASSESSMENT_TIMEOUT`으로 구분
- 평가 불가 시 실제 이체 API를 호출하지 않는 Fail-Closed 정책
- 정책 버전·점수·사유 코드·입력 피처를 JSON 스냅샷으로 저장
- 서버 타임존과 무관하게 FDS 요청 시간을 `Asia/Seoul`로 변환
- 최근 30일 완료 이체에서 평균·최대·모표준편차·수취인 수·주요 시간대 집계

| 위험도 | 결정 | 금융 처리 |
|---|---|---|
| LOW | ALLOW | 송금 실행 |
| MEDIUM | ALLOW_WITH_ALERT | 송금 실행 후 보호자 알림 |
| HIGH | BLOCK | 송금 미실행, 보호자 긴급 알림 |

### 보호자 등록

- 로그인한 본인 계정에만 등록 가능 (`@CurrentUser`, 요청 본문으로 사용자 ID를 받지 않음)
- 보호자 확인 절차 없이 즉시 `ACTIVE` — 확인 화면을 두지 않기로 해 초대·승인 단계가 없음
- 보호자가 Movi 회원이 아니어도 등록됨. 알림은 전화번호로 발송하며 `guardian_user_id`는 비움
- 본인 번호 등록 차단(`users.phone_hash` 대조), 같은 번호 중복 등록 차단
- 전화번호는 AES 암호화 저장. 무작위 IV라 암호문 비교로는 중복을 가릴 수 없어 활성 링크만 복호화해 대조

### 보호자 알림

- MEDIUM/HIGH 이체에서 활성 보호자별 알림 생성
- 송금 커밋 후 별도 트랜잭션에서 알림을 `QUEUED`로 저장
- 발송 결과를 `SENT` 또는 `FAILED`로 별도 기록
- 일시 실패 시 같은 알림 ID를 사용해 최대 3회 재시도
- 알림 실패가 완료·차단된 송금 상태를 되돌리지 않음

### SMS 발송 (솔라피)

- `SmsNotificationSender` 경계 뒤에 구현. `movi.sms.provider=solapi`일 때 활성화
- 설정이 없으면 기존 `UnavailableSmsNotificationSender`가 쓰여 발송 실패로 기록됨 (성공으로 위장하지 않음)
- local·test 프로필은 Mock 사용
- 실패를 예외로 올림 — 호출부가 이를 잡아 재시도 큐에 넣는 구조라, 삼키면 재시도가 일어나지 않음
- 요청마다 새 salt로 HMAC-SHA256 서명. 전화번호 원문과 API Secret은 로그에 남기지 않음
- **솔라피 API 키에 IP 허용 목록이 걸려 있어, 등록되지 않은 IP에서는 403이 납니다.** 로컬에서 실발송을 시험하려면 콘솔에 해당 IP를 추가해야 합니다

## 주요 기술적 의사결정

### 1. AI 결과를 신뢰 경계 밖 입력으로 취급

AI가 반환한 Intent와 Entity는 사용자 입력과 동일하게 재검증합니다. AI는 실제 계좌·수취인·사용자 DB에 접근하지 않으며 금융 상태를 변경할 권한도 없습니다.

### 2. FDS는 Fail-Closed

금융 MVP에서 평가 실패를 저위험으로 간주하면 장애가 곧 보안 우회로가 됩니다. 따라서 타임아웃, 통신 오류, 역직렬화 실패, 필수값 누락, 정의되지 않은 risk/decision 조합을 모두 송금 중단으로 처리합니다.

### 3. 멱등성은 애플리케이션과 DB에서 함께 보장

음성 명령과 모바일 네트워크는 중복 요청이 발생하기 쉽습니다. 애플리케이션 조회만으로는 동시성 경합을 막기 어려워 사용자 비관적 잠금과 DB UNIQUE 제약을 함께 사용합니다. 같은 키의 최종 요청은 기존 결과를 반환합니다.

### 4. 송금과 알림의 트랜잭션 분리

SMS Provider 장애 때문에 이미 완료된 송금이 롤백되면 금융 상태와 외부 상태가 어긋납니다. 송금 상태를 먼저 확정하고 알림 생성·발송·결과 기록을 분리했습니다.

### 5. 평문은 필요한 경계에서만 사용

전화번호, 오픈뱅킹 토큰, 수취 계좌번호는 AES-GCM으로 저장합니다. 검색이 필요한 값은 HMAC 해시를 별도로 사용하고, 평문은 외부 API 호출 직전에만 복호화합니다.

### 6. 외부 연동은 Mock과 실제 Adapter를 교체

AI와 금융 Sandbox 승인은 개발 일정과 독립적인 외부 변수입니다. 도메인 흐름을 Mock으로 먼저 완성하고 설정만으로 실제 HTTP 구현을 선택하도록 구성했습니다.

## 공개 API

모든 JSON 응답은 공통 `ApiResponse<T>`로 감싸며, 금융 결과와 오류에는 화면을 보지 않고도 이해할 수 있는 `voiceMessage`를 포함합니다.

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/v1/auth/kakao/authorize` | 카카오 인증 URL 생성 |
| `GET` | `/api/v1/auth/kakao/callback` | 카카오 OAuth callback |
| `POST` | `/api/v1/auth/pin/register` | PIN 최초 등록 |
| `POST` | `/api/v1/auth/pin/login` | PIN 로그인 |
| `POST` | `/api/v1/auth/token/refresh` | JWT 갱신 |
| `POST` | `/api/v1/auth/logout` | 로그아웃·기존 토큰 무효화 |
| `POST` | `/api/v1/guardian-links` | 보호자 등록 (이름·전화번호·관계) |
| `POST` | `/api/openbanking/connect` | 오픈뱅킹 연결 시작 |
| `GET` | `/api/openbanking/callback` | 오픈뱅킹 callback |
| `GET` | `/api/accounts` | 연결 계좌 목록 |
| `PATCH` | `/api/accounts/{accountId}/primary` | 기본 계좌 변경 |
| `PATCH` | `/api/accounts/{accountId}/alias` | 계좌 별칭 변경 |
| `GET` | `/api/accounts/balance` | 기본 또는 별칭 계좌 잔액조회 |
| `POST` | `/api/voice/sessions` | 음성 세션 시작 |
| `POST` | `/api/voice/sessions/{voiceSessionId}/commands` | 음성 분석·재질문·확인·취소·송금·거래내역·잔액 조회 |
| `GET` | `/api/transfers/status` | 멱등성 키로 송금 상태 복구 |
| `GET` | `/api/transactions` | 거래내역 필터·페이징 조회 |
| `GET` | `/api/transactions/{transactionId}` | 거래내역 단건 상세 조회 |

API 계약과 요청·응답 예시는 [통합 명세](docs/integration-spec.md), [API 응답 규약](docs/api-response.md)을 참고합니다.

## 로컬 실행

### 1. 설정 파일 준비

실제 설정 파일은 인증정보 보호를 위해 Git에서 제외합니다. `.example` 템플릿은 쓰지 않으므로 `application.yml`, `application-local.yml`, `application-test.yml`을 직접 만들고, 채워야 할 값(DB 비밀번호·API 키 등)은 팀 채널(Notion/카톡)에서 확인합니다.

암호화·JWT 키 예시는 다음처럼 생성할 수 있습니다.

```bash
openssl rand -base64 32
```

### 2. MySQL 준비

```bash
mysql -u root -p -e "CREATE DATABASE movi CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p movi < docs/schema.sql
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 로컬 설정은 Voice, FDS, 오픈뱅킹, SMS Mock을 사용합니다.

실제 문자를 보내려면 `movi.sms.provider`를 `solapi`로 바꾸고 아래를 채웁니다. 발신번호는 솔라피에 사전 등록된 번호여야 하고, API 키에 IP 허용 목록이 걸려 있으면 실행 위치의 IP도 콘솔에 등록해야 합니다.

```yaml
movi:
  sms:
    provider: solapi
    solapi:
      api-key: ...
      api-secret: ...
      sender-phone: ...
```

연동 상태만 빠르게 확인하려면 실발송 점검 테스트를 씁니다. 실제 문자가 나가고 비용이 들어 평소 테스트에서는 건너뜁니다.

```bash
SOLAPI_LIVE_TEST=true SOLAPI_API_KEY=키 SOLAPI_API_SECRET=시크릿 SOLAPI_SENDER_PHONE=발신번호 SOLAPI_TARGET_PHONE=수신번호 SOLAPI_MESSAGE=테스트 ./gradlew cleanTest test --tests "*SolapiLiveSendTest*"
```

### 4. 시연 데이터 (선택)

빈 DB에서는 로그인부터 막혀 시연도 E2E도 시작할 수 없습니다. 시드를 켜면 기동 시 데모 데이터를 만듭니다.

```yaml
movi:
  seed:
    enabled: true
```

`*.yml`은 애플리케이션 키로 암호화·해시한 값을 만들어야 해서 SQL 파일 대신 코드로 생성합니다. **이미 있으면 건너뛰고 아무것도 지우지 않습니다.**

| 항목 | 값 |
|---|---|
| 데모 사용자 | `01012345678` / PIN `135790` (`movi.seed.pin`으로 변경) |
| 소유권 검증용 타인 | `01099998888` / 같은 PIN |
| 신뢰 기기 | `movi.seed.device-uuid` — 프런트가 이 값을 보내야 LOW 판정이 나옵니다 |
| 계좌 | 생활비 통장(53만원, 기본) · 비상금 통장(120만원) |
| 수취인 | 엄마·아들(거래 이력 있음) · 김영희(첫 거래) |

세 위험도가 모두 재현됩니다.

| 위험도 | 시연 방법 |
|---|---|
| LOW | 엄마에게 10만원 이하 — 이체 완료, 알림 없음 |
| MEDIUM | 김영희에게 송금 또는 10만원 초과 — 이체 완료 + 보호자 알림 |
| HIGH | **비상금 통장에서** 70만원 이상 — 차단 + 보호자 알림 |

HIGH는 반드시 비상금 통장에서 보내야 합니다. 기본 계좌는 53만원이라 FDS가 아니라 잔액 부족에서 먼저 막힙니다.

### 5. 개발 인증

`movi.auth.dev-mode=true`인 로컬 환경에서만 다음 헤더로 사용자를 지정할 수 있습니다.

```http
X-Dev-User-Id: 3
```

운영 환경에서는 반드시 `movi.auth.dev-mode=false`로 설정합니다.

## 빌드와 테스트

```bash
./gradlew build
./gradlew test
./gradlew test --tests "*TransferExecutionServiceTest"
```

자동 테스트는 다음 위험 시나리오를 중점적으로 다룹니다.

- 같은 멱등성 키의 순차·동시 요청에서 외부 이체 1회 보장
- LOW/MEDIUM/HIGH별 송금·차단·보호자 알림 분기
- FDS 장애·잘못된 응답에서 오픈뱅킹 미호출
- 일일 한도와 확인 직전 잔액 부족
- 슬롯 만료·재질문 횟수 초과·다른 사용자 세션 접근
- 수취 계좌번호 복호화 실패 시 외부 API 미호출
- 오픈뱅킹 실제 거래 시각·잔액 반영
- 알림 실패가 금융 상태에 영향을 주지 않는지 검증
- FDS 30일 행동 프로필 통계와 스케줄 실행
- Repository·세션 만료·멱등성 경합 Spring 통합 테스트

## CI/CD와 운영

- `develop` push 시 Gradle 전체 빌드와 테스트
- Docker 이미지에 커밋 SHA와 `latest` 태그 부여
- Docker Hub push 후 EC2에 배포
- 비루트 컨테이너 실행과 읽기 전용 운영 설정 마운트
- Actuator health check 실패 시 이전 컨테이너로 롤백
- Nginx reverse proxy 설정 제공
- Prometheus 메트릭 엔드포인트 제공

현재 파이프라인 코드는 구현돼 있으며, 운영 JWT·DB·암호화 키를 포함한 서버 설정과 실제 외부 연동 기동 검증을 진행하고 있습니다.

## 프로젝트 구조

```text
src/main/java/com/movi_backend
├── domain
│   ├── account       # 계좌·잔액조회·오픈뱅킹
│   ├── auth          # OAuth·PIN·JWT·기기
│   ├── voice         # 음성 세션·슬롯·AI Voice
│   ├── transfer      # 송금 검증·실행·거래내역
│   ├── fds           # FDS 연동·평가·행동 프로필
│   └── guardian      # 보호자 관계·위험 알림
└── global
    ├── config
    ├── error
    ├── response
    ├── security
    └── util
```

## 로드맵

### P0 · MVP 종단 검증

- [ ] 운영 JWT·DB·암호화 설정으로 배포 헬스체크 통과
- [ ] 실제 AI Voice staging에서 모바일 녹음 파일 검증
- [ ] 실제 AI FDS staging에서 정상·timeout·잘못된 응답 시나리오 검증
- [ ] 오픈뱅킹 Sandbox에서 연결 → 잔액조회 → 송금 종단 검증
- [x] 로그인 → 보호자 등록 → 음성 → FDS → 송금 → 보호자 알림 전체 E2E 작성
- [ ] 배포 환경에서 `provider: solapi` 적용 후 실제 경고 문자 수신 확인

### P1 · 운영 안정성

- [x] 국내 SMS Provider Adapter 연결 (솔라피)
- [ ] 은행 거래고유번호 영속화와 사후 대사 흐름
- [ ] FDS 409 충돌 후 기존 평가 조회 계약 확정
- [ ] MySQL 백업·복구 시험
- [ ] HTTPS·Nginx·방화벽 운영 점검

### P2 · 확장

- [ ] 대용량 FDS 프로필 배치 페이징·성능 검증
- [ ] 외부 API 관측 지표와 장애 알림 강화
- [ ] 접근성 사용자 테스트 결과를 음성 문구와 세션 정책에 반영

## 팀 협업 기준

| 담당 | 주요 영역 |
|---|---|
| Jun | 오픈뱅킹 연동(Port/Adapter, Mock·실 API)·계좌 연결·조회·잔액조회, 공통 인프라(API 응답·에러 코드·인증 컨텍스트·엔티티 설계)·배포 파이프라인·프로젝트 문서 체계 |
| jjh | 인증·보호자 관계·알림·환경설정·로깅 |
| HANEUL MUN | 잔액조회·송금/이체·FDS 연동 |
| 공통 | 배포·시드데이터·E2E |

구현은 GitHub Issue → 작업 브랜치 → 테스트 → PR → `develop` 병합 순서로 진행합니다. 외부 계약이나 보안 정책이 바뀌면 관련 코드뿐 아니라 통합 명세와 README도 함께 갱신합니다.

## 상세 문서

- [도메인 가이드](docs/domain-guide.md)
- [프론트·AI·백엔드 통합 명세](docs/integration-spec.md)
- [AI Voice·FDS API 계약](docs/ai-api-contract.md)
- [API 응답 규약](docs/api-response.md)
- [에러 코드](docs/error-codes.md)
- [ERD](docs/ERD.md)
- [MySQL 스키마](docs/schema.sql)
- [실행 기준 — 완료 조건·담당·남은 일](docs/execution-plan.md)

## README 유지 원칙

README는 개발이 끝난 뒤 한 번 작성하는 소개 문서가 아니라 현재 프로젝트 상태를 설명하는 운영 문서로 관리합니다.

- 사용자 흐름이나 핵심 정책이 바뀌면 해당 PR에서 README를 함께 수정합니다.
- 외부 연동은 `구현 완료`와 `실환경 검증 완료`를 구분합니다.
- 완료하지 않은 기능을 구현된 것처럼 표현하지 않습니다.
- 성능·사용자 수·정확도 같은 수치는 재현 가능한 근거가 있을 때만 기록합니다.
- 중요한 장애와 해결 과정은 기술적 의사결정 또는 상세 문서로 남깁니다.
- 큰 마일스톤마다 문서 기준일과 로드맵 체크 상태를 갱신합니다.
