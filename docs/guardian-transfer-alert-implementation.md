# 7.x 보호자 연결 · 10.x 이체 알림 구현 노트

대상 기능명세:
`07-01`, `07-04-07`, `10-01`, `10-03`

> **2026-08-20 갱신** — 보호자 등록을 초대·승인 방식에서 회원가입 시 즉시 등록 방식으로
> 바꾸면서 `07-02-03`(연결 승인) 기능명세는 삭제했다. 아래 §1·§2.1·§2.2·§2.4·§4·§5의
> 보호자 관련 서술 중 "초대"·"승인"을 언급하는 부분은 이 갱신 이전 내용이니
> `07-01-guardian-registration-request.md` v2를 최신으로 본다.

이 문서는 **무엇을 구현했고, 무엇을 아직 못 했으며, 외부 연동이 열리면 어디만 바꾸면 되는지**를 정리한다.

---

## 1. 구현 범위

### 보호자 (7.1 / 7.2 / 7.3 / 7.4 / 7.7)

| 엔드포인트 | 설명 | 인증 |
|---|---|---|
| `POST /api/v1/guardian-links` | 보호자 등록 요청 + 초대 SMS | 필요 |
| `GET /api/v1/guardian-links/invitations/{inviteToken}` | 초대 내용 확인 | **불필요** |
| `POST /api/v1/guardian-links/invitations/{inviteToken}/approve` | 연결 승인 | 필요 |
| `POST /api/v1/guardian-links/invitations/{inviteToken}/reject` | 연결 거절 | 필요 |

초대 확인만 인증 없이 연 이유: 보호자는 아직 미가입일 수 있다. 로그인을 먼저 요구하면
무엇에 동의하는지 모른 채 가입하게 된다.

### 이체 (10.1 / 10.3)

| 엔드포인트 | 설명 |
|---|---|
| `POST /api/v1/transfers` | 이체 실행. 고위험이면 실행하지 않고 확인 대기로 응답 |
| `POST /api/v1/transfers/{transferId}/confirm` | 본인 재확인 "네". 이 요청을 받아야 송금이 나간다 |
| `POST /api/v1/transfers/{transferId}/decline` | 본인 재확인 "아니요". 차단으로 확정 |

```text
멱등성 확인 → PENDING 생성 → 잔액 조회 → RISK_REVIEW → FDS 평가
     ├─ HIGH/BLOCK → 오픈뱅킹 호출 안 함 → HOLD 확정
     │                 → 보호자 + 본인에게 고위험 감지 알림(비동기)
     │                 → 본인 응답 대기 (기본 5분)
     │                      ├─ confirm → 오픈뱅킹 이체 → COMPLETED → 보호자 통보
     │                      ├─ decline → BLOCKED → 보호자 통보
     │                      └─ 무응답  → BLOCKED → 보호자 통보
     └─ LOW/MEDIUM → 오픈뱅킹 이체 → COMPLETED → (MEDIUM이면 보호자 통보)
```

### 고위험 재확인 (v1.1 정책)

고위험이라고 곧바로 확정 차단하지 않는다. 오탐이 났을 때 사용자가 스스로 풀 방법이 없으면
평소보다 큰 금액을 보내는 정상 거래까지 막혀 서비스를 못 쓴다.

알림은 **보호자와 본인 양쪽**으로 나간다. 본인이 요청하지 않은 이체라면 계좌 주인이 가장 먼저
알아야 하고, 전화로 지시받는 중이라면 보호자가 알아야 한다. 상황별 문구를 나눠 두었다.

| 상황 | 보호자 | 본인 |
|---|---|---|
| 고위험 감지 (확인 대기) | `HIGH_RISK_DETECTED_ALERT` | `HIGH_RISK_SELF_ALERT` |
| 본인 확인 후 진행 | `HIGH_RISK_CONFIRMED_ALERT` | 음성 안내 |
| 본인 거절 · 시간 초과 | `BLOCKED_TRANSFER_ALERT` | 음성 안내 |

보호자 입장에서 "막힌 건지 나간 건지"를 구분하지 못하면 알림이 무의미하다.

**확인 대기 상태에서도 오픈뱅킹은 호출하지 않는다.** 사용자가 "네"라고 답하기 전까지 돈은
그대로다. `movi.transfer.confirmation-expire-minutes`(기본 5분)가 지나면 차단으로 확정한다.

재질문 문구는 템플릿으로 고정했다.

```text
"안전을 위해 잠시 멈췄어요. 평소와 다른 송금으로 보여요.
 김민수 님에게 오만 원, 정말 보내시겠어요? 보내시려면 네, 아니면 아니요라고 말씀해 주세요."
```

물어볼 때마다 표현이 달라지면 화면을 보지 못하는 사용자는 무엇을 묻는 건지 매번 새로 파악해야 한다.

---

## 2. 설계상 중요한 결정

### 2.1 전화번호 중복 판별은 해시로 한다

`guardian_links.guardian_phone`은 AES-GCM 무작위 IV로 암호화되어 **같은 번호도 매번 다른 암호문**이
된다. 암호문 비교로는 중복 초대를 막을 수 없다.

`users.phone_hash`와 같은 원칙으로 `guardian_phone_hash`(HMAC-SHA256) 컬럼을 추가했다.

- 스키마: `docs/schema.sql`, `docs/ERD.md`, ERDCloud SQL 갱신 완료
- 마이그레이션: `docs/migrations/20260819_add_guardian_links_phone_hash.sql`
- **운영 DB에 적용하지 않으면 `ddl-auto: validate`에서 기동이 실패한다**

### 2.2 동시 승인은 조건부 UPDATE로 막는다

```sql
UPDATE guardian_links
   SET guardian_user_id = ?, status = 'ACTIVE', accepted_at = ?
 WHERE link_id = ? AND status = 'REQUESTED' AND invite_expires_at > ?
```

영향받은 행이 0이면 이미 처리됐거나 만료된 것으로 보고 `GUARDIAN_4091`로 변환한다.
같은 링크에 승인이 동시에 들어와도 한 건만 성공한다.

### 2.2-1 `HOLD` 상태를 추가했다

`TransferStatus`에 `HOLD`(확인 대기)를 추가했다. 전이는 아래로만 허용한다.

```text
RISK_REVIEW → HOLD → COMPLETED   (본인 재확인 후 진행)
                   → BLOCKED     (본인 거절 · 확인 시간 초과)
                   → FAILED      (재확인 후 외부 연동 실패)
```

만료 시각은 별도 컬럼 없이 `requested_at + 설정값`으로 계산한다. 설정을 바꾸면 대기 중인
건에도 바로 반영되고 스키마를 늘리지 않아도 된다.

마이그레이션: `docs/migrations/20260819_add_transfer_hold_status.sql` (컬럼 코멘트만 갱신)

### 2.3 트랜잭션 경계를 쪼갠 이유

`TransferFacade`에는 `@Transactional`이 없다. 일부러 그렇게 뒀다.

- FDS·오픈뱅킹 응답을 기다리는 동안 DB 커넥션과 락을 잡고 있지 않는다.
- **차단 상태를 먼저 확정(트랜잭션 A)한 뒤 알림을 보낸다(트랜잭션 B).**
  SMS Provider가 죽어도 `BLOCKED`는 롤백되지 않는다.

보호자 알림은 `AsyncNotificationDispatcher`가 별도 스레드에서 처리한다.
`NotificationService`와 클래스를 분리한 이유는 같은 빈 안에서 `@Async`를 호출하면
프록시를 타지 않아 그냥 동기 실행되기 때문이다.

### 2.4 차단 응답에서 "보호자에게 알렸다"고 말하지 않는다

알림은 비동기라 응답 시점에 발송 성공을 단정할 수 없다.

```text
"안전을 위해 이체를 중단했어요."
```

실제로 일어난 사실만 안내한다. 초대 SMS는 동기 발송이라 성공/실패를 구분해 문구를 바꾼다.

### 2.5 금액은 한국어로 변환해 내려준다

`KoreanAmountFormatter.toKoreanWon(50000)` → `"오만 원"`

TTS가 `50000원`을 어떻게 읽을지 보장할 수 없다.

---

## 3. 아직 대역(Mock)인 것 — 연동이 열리면 여기만 바꾼다

| 대상 | 인터페이스 | 대역 | 전환 방법 |
|---|---|---|---|
| FDS 추론 | `FdsClient` | `MockFdsClient` (금액 기준 판정) | `movi.fds.mode=http` + `base-url` |
| 오픈뱅킹 | `OpenBankingClient` | `MockOpenBankingClient` | `movi.openbanking.mode=http` (구현체 추가 필요) |
| SMS | `SmsProvider` | `MockSmsProvider` (로그만) | `movi.sms.provider=<이름>` + 구현체 추가 |

`HttpFdsClient`는 `docs/ai-api-contract.md` 계약대로 이미 작성돼 있다.
AI 파트 API가 열리면 설정만 바꾸면 된다.

`MockFdsClient` 판정 규칙(설정으로 조절 가능):

```text
amount >= movi.fds.mock-high-amount   (기본 100만) -> HIGH   + BLOCK
amount >= movi.fds.mock-medium-amount (기본 30만)  -> MEDIUM + ALLOW_WITH_ALERT
처음 보내는 상대 + 이력 없음                        -> MEDIUM + ALLOW_WITH_ALERT
그 외                                              -> LOW    + ALLOW
```

**실제 이상거래 탐지가 아니다.** 백엔드 분기 흐름(차단·알림·완료)을 끝까지 검증하기 위한 것이다.

---

## 4. 명세가 확정하지 않아 설정으로 뺀 것

| 항목 | 설정 키 | 현재 기본값 | 비고 |
|---|---|---|---|
| `permission_scope`가 비어 있을 때 알림 수신 여부 | `movi.guardian.default-receive-alert` | `true` | **팀 정책 확정 필요** |
| 알림 발송 재시도 | — | 재시도 없음 (1회 후 FAILED) | 명세에 backoff 정책 없음 |
| 고위험 재확인 대기 시간 | `movi.transfer.confirmation-expire-minutes` | 5분 | v1.1에서 추가 |

새로 만드는 연결은 항상 `{"view_balance":true,"receive_alert":true}`를 명시적으로 저장하므로
기본값이 쓰이는 경우는 과거 데이터뿐이다.

---

## 5. 남은 일 (다음 작업자에게)

0. **고위험 재확인에 PIN 재인증 추가** — 지금은 음성 답변 한 번으로 통과한다.
   보이스피싱범이 옆에서 "네라고 말하세요"를 유도하면 이 절차는 막지 못한다.
   `AuthenticationService`에 PIN 검증이 이미 있으므로 `confirm` 요청에 PIN을 함께 받아
   검증하는 정도면 붙는다. **운영 전환 전 필수 검토 항목이다.**
1. **보호자 전화번호 소유 검증** — 등록은 확인 절차 없이 즉시 성립하므로, 입력한 번호가
   실제로 그 사람 소유인지는 전혀 검증하지 않는다. 잘못된 번호를 입력해도 등록이 막히지 않는다.
   운영 전환 전에 SMS OTP 등으로 소유권을 확인하는 절차 추가를 검토해야 한다.
2. **본인 계좌 이체 차단** — `accounts.account_num_masked`가 암호화된 마스킹 값이라
   수취 계좌번호와 안전하게 대조할 수 없다. `ErrorCode.SELF_TRANSFER_NOT_ALLOWED`는
   아직 어디서도 던지지 않는다. 계좌번호 해시 컬럼을 두는 방식이 필요하다.
3. **`user_transfer_profiles` 갱신 배치** — 프로필이 비어 있으면 전부 cold start로 평가된다.
4. **오픈뱅킹 실제 어댑터** — `OpenBankingClient` 구현체만 추가하면 된다.
5. **보호자 대시보드 조회 API** — 이번 범위 밖.

---

## 6. 새로 추가한 에러 코드

| 코드 | Enum | HTTP |
|---|---|---:|
| `GUARDIAN_4005` | `INVALID_GUARDIAN_RELATION` | 400 |
| `GUARDIAN_4090` | `DUPLICATE_GUARDIAN_REQUEST` | 409 |
| `GUARDIAN_4091` | `GUARDIAN_LINK_ALREADY_PROCESSED` | 409 |
| `TRANSFER_4007` | `TRANSFER_NOT_AWAITING_CONFIRMATION` | 400 |
| `TRANSFER_4008` | `TRANSFER_CONFIRMATION_EXPIRED` | 400 |

"이미 연결된 보호자"는 기존 `GUARDIAN_4001 ALREADY_LINKED`를 재사용했다.
