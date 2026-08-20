# 10.3 긴급 위험 알림

버전: `v1.1`  
대상: Spring Backend  
기능 ID: `10.3 긴급 위험 알림`

---

## 0. v1.1 정책 변경 (2026-08-19)

**고위험 거래를 즉시 확정 차단하지 않고, 본인에게 한 번 더 묻는다.**

v1.0은 `HIGH + BLOCK`을 받으면 곧바로 `transfers.status = BLOCKED`로 확정했다.
v1.1에서는 확인 대기 상태를 거친다.

```text
v1.0:  HIGH + BLOCK → BLOCKED (확정) → 보호자 알림

v1.1:  HIGH + BLOCK → HOLD (확인 대기) → 보호자 + 본인 알림
                        ├─ 본인 "네"    → 이체 실행 → COMPLETED → 보호자 통보
                        ├─ 본인 "아니요" → BLOCKED → 보호자 통보
                        └─ 무응답(기본 5분) → BLOCKED → 보호자 통보
```

변경한 이유:

- 오탐이 났을 때 사용자가 스스로 풀 방법이 v1.0에는 없었다. 평소보다 큰 금액을 보내는
  정상 거래까지 막히면 서비스를 못 쓴다.
- 본인에게도 알려야 한다. v1.0은 보호자에게만 알렸는데, 정작 계좌 주인은 자기 계좌에서
  무슨 일이 벌어지는지 몰랐다.

유지되는 것:

- `HOLD` 상태에서도 **오픈뱅킹은 호출하지 않는다.** 사용자가 확인하기 전까지 돈은 그대로다.
- FDS 계약(`LOW+ALLOW` / `MEDIUM+ALLOW_WITH_ALERT` / `HIGH+BLOCK`)은 그대로다.
  백엔드가 `BLOCK`을 "즉시 차단"이 아니라 "본인 확인 없이는 진행 불가"로 해석할 뿐이다.
- FDS 평가 자체가 실패하면 여전히 이체하지 않는다. 이 경우엔 확인 절차도 제공하지 않는다.
- 보호자에게 이체 승인 권한은 없다. 확인 권한은 본인에게만 있다.

### 남은 위험

재확인은 **음성 답변 한 번**으로 통과한다. 별도 PIN·생체 재인증을 요구하지 않는다.
보이스피싱범이 옆에서 "네라고 말하세요"를 유도하면 이 절차는 막지 못한다.
운영 전환 전에 재확인 단계의 PIN 재인증 도입을 검토해야 한다.

---

## 1. 목적

FDS가 이체를 `HIGH + BLOCK`으로 판정하면
실제 오픈뱅킹 이체를 실행하지 않고 거래를 차단한 뒤,
활성 보호자에게 SMS 또는 Push 긴급 알림을 보낸다.

기능명세:

```text
고위험 거래를 차단하고 보호자에게 SMS 또는 긴급 알림을 보낸다.
```

---

## 2. 절대 규칙

AGENTS.md의 FDS 정책을 따른다.

```text
LOW    -> ALLOW
MEDIUM -> ALLOW_WITH_ALERT
HIGH   -> BLOCK
```

HIGH 거래에서는 반드시:

```text
1. Open Banking transfer 호출 금지
2. transfers.status = BLOCKED
3. FDS 평가 결과 저장
4. 보호자 긴급 알림 생성
5. 사용자에게 차단 사실 음성 안내
```

순서로 처리한다.

FDS 호출 자체가 실패한 경우에도 이체를 통과시키지 않는다.
다만 FDS **장애**와 FDS **HIGH 판정**은 서로 다른 상태로 취급한다.

---

## 3. FDS 계약

AI FDS 정상 응답의 HIGH 조합:

```json
{
  "riskLevel": "HIGH",
  "decision": "BLOCK"
}
```

다음 조합만 유효하다.

```text
LOW    + ALLOW
MEDIUM + ALLOW_WITH_ALERT
HIGH   + BLOCK
```

예:

```text
HIGH + ALLOW
LOW + BLOCK
```

처럼 불일치하면 `ASSESSMENT_FAILED`로 처리하고
실제 이체를 호출하지 않는다.

---

## 4. 관련 테이블

### transfers

```text
transfer_id
user_id
from_account_id
amount
status
requested_at
```

HIGH 판정 후:

```text
status = BLOCKED
```

### fds_assessments

저장:

```text
transfer_id
user_id
model_version
anomaly_score
risk_level = HIGH
decision   = BLOCK
features
latency_ms
evaluated_at
```

### guardian_links

알림 대상:

```text
protectee_user_id = transfer.user_id
status            = ACTIVE
```

추가로 `permission_scope`에서
위험 알림 수신 권한을 사용한다면:

```json
{
  "receive_alert": true
}
```

인 관계만 대상으로 한다.

`permission_scope`가 NULL일 때의 기본값은 현재 명세에서 확정되지 않았으므로
임의로 true/false를 결정하지 않는다.

### notifications

권장 값:

```text
user_id       = 가입된 guardian_user_id
link_id       = guardian_links.link_id
transfer_id   = 현재 transfer_id
channel       = SMS 또는 PUSH
template_code = BLOCKED_TRANSFER_ALERT
status        = QUEUED -> SENT / FAILED
```

미가입 보호자는 `user_id = NULL`이고
`target_phone`을 이용해 SMS를 보낼 수 있다.

---

## 5. 처리 흐름

```text
Transfer request
      ↓
FDS /internal/v1/fraud/predict
      ↓
HIGH + BLOCK
      ↓
fds_assessments 저장
      ↓
transfers.status = BLOCKED
      ↓
ACTIVE guardian_links 조회
      ↓
notification QUEUED 생성
      ↓
SMS/PUSH 발송
      ↓
SENT 또는 FAILED
      ↓
사용자에게 차단 voiceMessage 반환
```

---

## 6. Transaction Boundary

중요한 원칙:

**알림 발송 실패가 차단된 거래를 다시 살려서는 안 된다.**

권장 분리:

### Transaction A

```text
FDS 결과 저장
+
transfer BLOCKED 변경
```

여기까지 금융 안전 상태를 먼저 확정한다.

### Transaction B

```text
notifications 생성 및 발송
```

Notification Provider 장애가 발생해도:

```text
transfer.status = BLOCKED 유지
```

한다.

---

## 7. 알림 대상 조회

기본 조건:

```text
guardian_links.protectee_user_id = transfer.user_id
guardian_links.status = ACTIVE
```

보호자가 여러 명이면 활성 보호자 각각에게 알림을 생성할 수 있다.

중복 발송 방지를 위해 동일한:

```text
transfer_id + link_id + template_code
```

조합을 두 번 처리하지 않는 것이 바람직하다.

현재 `notifications` 테이블에는 해당 UNIQUE 제약이 없으므로
Service 레벨 멱등 처리 또는 스키마 보완이 필요할 수 있다.

### Specification Gap

```text
대상:
10.3

불명확:
동일 고위험 거래에 대한 Notification 중복 발송 방지 기준

권장:
transferId + linkId + templateCode를 논리적 멱등 키로 사용
```

---

## 8. SMS 메시지

예:

```text
[Movi] 보호 대상자의 고위험 이체 요청이 감지되어 거래를 차단했습니다.
앱에서 거래 내역을 확인해 주세요.
```

SMS에 다음 정보를 넣지 않는 것을 기본으로 한다.

```text
전체 계좌번호
PIN
토큰
전화번호
FDS 내부 모델 score 전체
AI 내부 reason 원문
```

금액·수취인명을 SMS에 포함할지는 개인정보 노출 범위 정책이 필요하다.

MVP에서 명세가 없다면 최소 정보만 발송한다.

---

## 9. 사용자 Response

HIGH 거래에 대한 API 응답 예:

```json
{
  "success": true,
  "data": {
    "transferId": 101,
    "status": "BLOCKED",
    "riskLevel": "HIGH"
  },
  "voiceMessage": "안전을 위해 이체를 중단했습니다. 보호자에게 알렸습니다."
}
```

실제 SMS가 실패했는데 "보호자에게 알렸습니다"라고 말하면 안 된다.

알림 발송을 비동기로 처리한다면 더 안전한 문구:

```text
"안전을 위해 이체를 중단했습니다."
```

사용자에게 FDS 모델의 기술 용어를 읽지 않는다.

금지:

```text
"Isolation Forest anomaly score가 0.82입니다."
```

---

## 10. Notification 상태

생성:

```text
QUEUED
```

성공:

```text
QUEUED -> SENT
sent_at 저장
provider_msg_id 저장
```

실패:

```text
QUEUED -> FAILED
```

Provider 오류 메시지 전체를 `payload`나 로그에 무분별하게 저장하지 않는다.

---

## 11. 비동기 처리

SMS/PUSH 발송을 `@Async`로 구현한다면
현재 프로젝트 규칙상 `@Transactional`을 함께 적용한다.

단, 비동기 메서드 호출이 동일 클래스 내부 self-invocation이 되지 않도록 주의한다.

예:

```text
TransferService
   ↓
NotificationService
   ↓
AsyncNotificationSender
```

외부 Provider 호출 실패가 원 거래 트랜잭션을 rollback시키지 않도록
트랜잭션 경계를 분리한다.

---

## 12. 재시도

현재 기능명세에는 SMS Provider 재시도 횟수가 정의되어 있지 않다.

임의로 무한 재시도를 구현하지 않는다.

Specification Gap:

```text
알림 발송 실패 시 자동 재시도 횟수와 backoff 정책
```

MVP에서는:

```text
1회 요청
실패 시 FAILED 기록
```

로 단순화할 수 있으나 팀 정책 확정 후 적용한다.

---

## 13. 보호자가 없는 경우

HIGH 거래 자체는 그대로 차단한다.

```text
보호자 없음
    ↓
BLOCKED 유지
```

보호자가 없다고 HIGH 거래를 허용해서는 안 된다.

알림 대상이 없음을 로그/감사 정보로 기록할 수 있다.

사용자 응답에서는:

```text
"안전을 위해 이체를 중단했습니다."
```

처럼 실제 수행된 사실만 안내한다.

---

## 14. 필수 테스트

### HIGH 분기

```text
HIGH_BLOCK을_받으면_이체를_BLOCKED로_변경한다
HIGH_BLOCK을_받으면_오픈뱅킹을_호출하지_않는다
HIGH_BLOCK을_받으면_FDS_평가를_저장한다
```

### 보호자 알림

```text
HIGH_거래가_차단되면_ACTIVE_보호자에게_긴급_알림을_생성한다
보호자가_여러_명이면_각_보호자에게_알림을_생성한다
REJECTED_보호자에게는_알림을_보내지_않는다
REVOKED_보호자에게는_알림을_보내지_않는다
보호자가_없어도_HIGH_거래는_BLOCKED를_유지한다
```

### Provider

```text
SMS_발송에_성공하면_notification을_SENT로_변경한다
SMS_발송에_실패하면_notification을_FAILED로_변경한다
SMS_발송에_실패해도_transfer는_BLOCKED를_유지한다
```

### 보안

```text
긴급_알림_로그에_전체_계좌번호를_남기지_않는다
긴급_알림_로그에_전화번호_원문을_남기지_않는다
```

---

## 15. 완료 조건

- [ ] HIGH + BLOCK 조합을 검증한다.
- [ ] HIGH 거래에서 Open Banking을 호출하지 않는다.
- [ ] `transfers.status=BLOCKED`를 먼저 확정한다.
- [ ] ACTIVE 보호자만 조회한다.
- [ ] 알림 수신 권한 정책을 명확히 적용한다.
- [ ] `notifications` 행을 생성한다.
- [ ] SMS 또는 Push Provider를 호출한다.
- [ ] 발송 결과에 따라 SENT/FAILED로 변경한다.
- [ ] 알림 실패가 BLOCKED 상태를 rollback하지 않는다.
- [ ] 보호자가 없어도 BLOCKED 상태를 유지한다.
- [ ] 민감정보를 로그에 남기지 않는다.
- [ ] 관련 테스트가 통과한다.
- [ ] `./gradlew build`가 통과한다.
