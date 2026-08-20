# 10.1 이체 완료 알림

버전: `v1.0`  
대상: Spring Backend  
기능 ID: `10.1 이체 완료 알림`

---

## 1. 목적

오픈뱅킹 이체가 실제로 정상 완료된 경우,
화면을 보지 않는 사용자도 결과를 알 수 있도록
백엔드 응답의 `voiceMessage`로 이체 완료 사실을 전달한다.

기능명세상 핵심은 **사용자 음성 안내**다.

이번 MVP에서 이 기능 때문에 별도의 SMS 또는 Push를 필수로 추가하지 않는다.

---

## 2. 사전조건

완료 알림은 다음 조건에서만 만들어진다.

```text
1. AI가 추출한 필수 이체값을 백엔드가 검증함
2. 멱등성 검증 완료
3. FDS 평가 완료
4. FDS 결과가 실제 실행 가능한 정책임
5. Open Banking 이체 성공 응답 수신
6. transfers.status가 COMPLETED로 확정됨
```

FDS 평가 실패 상태에서 완료 알림을 반환하면 안 된다.

Open Banking 응답이 불명확한 경우에도
성공으로 추정하지 않는다.

---

## 3. 현재 FDS 정책

AGENTS.md 기준:

```text
LOW    -> ALLOW
MEDIUM -> ALLOW_WITH_ALERT
HIGH   -> BLOCK
```

따라서:

```text
LOW
  -> 이체 성공
  -> COMPLETED
  -> 사용자 완료 안내

MEDIUM
  -> 이체 성공
  -> COMPLETED
  -> 사용자 완료 안내
  -> 보호자 위험 알림 별도 처리

HIGH
  -> BLOCKED
  -> 이체 완료 알림 생성 금지
```

보호자 거래 승인 기능은 현재 MVP 범위가 아니다.

---

## 4. 처리 시점

```text
Open Banking Transfer Success
        ↓
transfers.status = COMPLETED
        ↓
completed_at 기록
        ↓
TransferResponse 생성
        ↓
ApiResponse.success(data, voiceMessage)
```

상태 변경 전에 완료 메시지를 만들어 반환하지 않는다.

---

## 5. Response

예:

```json
{
  "success": true,
  "data": {
    "transferId": 101,
    "status": "COMPLETED",
    "amount": 50000,
    "recipientName": "김민수",
    "completedAt": "2026-08-17T17:30:00+09:00"
  },
  "voiceMessage": "김민수 님에게 오만 원을 보냈습니다."
}
```

공통 응답 구조는 기존 `ApiResponse` 구현을 따른다.
위 JSON 키는 프로젝트 실제 `ApiResponse` 구조가 다르면 기존 구조를 우선한다.

---

## 6. 음성 메시지 생성 규칙

### 금액

숫자를 그대로 조합하지 않는다.

금지:

```text
"김민수 님에게 50000원을 보냈습니다."
```

권장:

```text
"김민수 님에게 오만 원을 보냈습니다."
```

프로젝트의 한국어 금액 변환 유틸이 있으면 재사용한다.
없다면 공용 유틸 추가 여부를 먼저 확인한다.

### 수취인

표시 가능한 이름을 사용한다.

우선순위 예:

```text
1. transfer recipient nickname
2. toHolderName
```

계좌번호 전체를 voiceMessage에 읽지 않는다.

### 실패

실패 응답에서 외부 API 오류 문구를 그대로 읽지 않는다.

예:

```text
OpenBankingSocketTimeoutException
```

같은 기술 용어가 사용자에게 전달되어서는 안 된다.

---

## 7. DB 변경

성공 시:

```text
transfers.status       = COMPLETED
transfers.completed_at = now
```

`fail_reason`은 성공 거래에 새로 기록하지 않는다.

---

## 8. notification 테이블 사용 여부

현재 `notifications` 테이블은 다음과 같은
SMS/PUSH 계열 전달 이력을 위한 구조다.

```text
channel: SMS / PUSH
template_code
target_phone
provider_msg_id
```

10.1 요구사항은 사용자 **음성 안내**이므로
기본 구현에서는 `notifications` 행 생성이 필수는 아니다.

추후 "이체 완료 Push"가 별도 요구사항으로 추가될 경우:

```text
template_code = TRANSFER_COMPLETED
channel       = PUSH
```

같은 확장을 별도 명세로 정의한다.

현재 범위를 임의로 넓히지 않는다.

---

## 9. 멱등성과 알림

동일 `idempotency_key` 요청이 반복되더라도
실제 이체는 한 번만 수행되어야 한다.

이미 완료된 동일 요청이 재조회되는 경우:

```text
- 기존 COMPLETED 결과 반환 가능
- 새 이체 생성 금지
- Open Banking 재호출 금지
```

voiceMessage를 다시 반환하는 것은 가능하다.

---

## 10. 예외

### 잔액 부족

```text
이체 수행 안 함
COMPLETED 금지
완료 voiceMessage 금지
```

### FDS 실패

```text
실제 이체 수행 안 함
COMPLETED 금지
완료 voiceMessage 금지
```

### HIGH

```text
BLOCKED
10.3 긴급 위험 알림 처리
완료 voiceMessage 금지
```

### Open Banking 실패

```text
FAILED
ErrorCode.voiceMessage 사용
완료 voiceMessage 금지
```

---

## 11. 감사 로그

이체 완료 시 프로젝트 감사 로그 정책에 따라 기록할 수 있다.

예:

```text
actor_type    = USER
action        = TRANSFER_COMPLETED
resource_type = TRANSFER
resource_id   = transfer_id
```

detail에 다음 정보를 원문으로 저장하지 않는다.

```text
to_account_num
access_token
전화번호
```

---

## 12. 필수 테스트

```text
LOW_이체가_완료되면_COMPLETED_상태가_된다
MEDIUM_이체가_완료되면_COMPLETED_상태가_된다
이체가_완료되면_completed_at이_기록된다
이체가_완료되면_사용자_음성_메시지를_반환한다
이체_완료_메시지는_금액을_한국어로_표현한다

HIGH_거래에는_이체_완료_메시지를_반환하지_않는다
FDS_평가가_실패하면_이체_완료_메시지를_반환하지_않는다
오픈뱅킹_이체가_실패하면_완료_메시지를_반환하지_않는다

같은_멱등성_키로_재요청해도_실제_이체는_한_번만_수행된다
```

외부 Open Banking 호출은 Mock으로 검증한다.

---

## 13. 완료 조건

- [ ] 실제 이체 성공 이후에만 COMPLETED 처리한다.
- [ ] `completed_at`을 기록한다.
- [ ] 완료 응답에 `voiceMessage`가 존재한다.
- [ ] 금액이 TTS 친화적인 한국어로 표현된다.
- [ ] 계좌번호 전체를 읽지 않는다.
- [ ] FDS 실패/High/Open Banking 실패에서 완료 메시지를 만들지 않는다.
- [ ] 멱등성 재요청에서 이체가 중복 실행되지 않는다.
- [ ] 관련 테스트가 통과한다.
- [ ] `./gradlew build`가 통과한다.
