# 7.1 보호자 등록 요청 · SMS 발송 연동

버전: `v1.0`  
대상: Spring Backend  
기능 ID: `7.1 보호자 등록 요청`  
연계 기능: `보호자 SMS 발송`

---

## 1. 목적

이용자가 보호자의 이름·전화번호·관계를 입력하여 보호자 연결을 요청한다.

백엔드는 요청 정보를 `guardian_links`에 `REQUESTED` 상태로 저장하고,
보호자에게 연결 확인용 초대 링크를 SMS로 발송한다.

이 기능은 **보호자 연결 관계를 즉시 활성화하지 않는다.**
실제 연결은 보호자가 요청을 확인하고 승인한 뒤 `ACTIVE` 상태가 되었을 때 성립한다.

---

## 2. 관련 테이블

### guardian_links

사용 컬럼:

| 컬럼 | 용도 |
|---|---|
| `link_id` | 보호자 연결 요청 식별자 |
| `protectee_user_id` | 보호를 요청한 이용자 |
| `guardian_user_id` | 보호자 가입·승인 전에는 `NULL` |
| `guardian_name` | 보호자 이름 |
| `guardian_phone` | AES 암호화한 보호자 전화번호 |
| `relation` | 보호자와의 관계 |
| `status` | 최초 `REQUESTED` |
| `invite_token` | SMS 초대 링크용 토큰 |
| `invite_expires_at` | 초대 링크 만료 시각 |
| `permission_scope` | 보호자 권한 범위 |
| `requested_at` | 요청 생성 시각 |

### notifications

SMS 발송 이력을 저장한다.

권장 값:

```text
channel       = SMS
template_code = GUARDIAN_INVITE
status        = QUEUED -> SENT / FAILED
link_id       = 생성된 guardian_links.link_id
transfer_id   = NULL
```

---

## 3. Endpoint

```http
POST /api/v1/guardian-links
Authorization: Bearer <JWT>
Content-Type: application/json
```

현재 사용자는 `@CurrentUser AuthUser`로 받는다.
`protecteeUserId`를 Request Body로 받지 않는다.

---

## 4. Request

```json
{
  "guardianName": "김보호",
  "guardianPhone": "01012345678",
  "relation": "자녀"
}
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `guardianName` | string | 예 | 공백 제거 후 빈 값 불가 |
| `guardianPhone` | string | 예 | 서버에서 정규화 후 검증 |
| `relation` | string | 예 | 관계 정보로 저장 |

전화번호는 저장 전에 정규화한다.

예:

```text
010-1234-5678
010 1234 5678
01012345678
    ↓
01012345678
```

DB에는 원문 전화번호를 저장하지 않는다.
`guardian_phone`에는 AES 암호화 값을 저장한다.

---

## 5. 처리 순서

```text
1. JWT 인증
2. 현재 사용자 조회
3. 요청 DTO 검증
4. 보호자 전화번호 정규화
5. 자기 자신을 보호자로 요청하는지 검증
6. 기존 중복 연결/요청 여부 검증
7. inviteToken 생성
8. inviteExpiresAt 계산
9. guardian_links REQUESTED 생성
10. notifications QUEUED 생성
11. SMS 발송 요청
12. 발송 성공 시 SENT, 실패 시 FAILED
13. ApiResponse 반환
```

연결 요청 생성과 알림 이력 생성은 데이터 정합성을 유지해야 한다.

SMS 발송 실패 때문에 이미 생성된 보호자 연결 요청을 삭제하지 않는다.
사용자에게는 "요청은 생성되었으나 문자 발송에 실패했다"는 식의
서비스 정책이 필요하며, 정확한 ErrorCode는 기존 `ErrorCode`를 먼저 확인한다.

---

## 6. 초대 토큰

`invite_token`은 추측하기 어려운 난수로 생성한다.

권장:

```text
SecureRandom 기반 256-bit 이상
또는
UUID + 추가 난수
```

금지:

```text
linkId
userId
전화번호
현재 시각
```

초대 링크 예:

```text
https://<frontend-domain>/guardian/invite?token=<inviteToken>
```

토큰은 다음 위치에 출력하지 않는다.

- application log
- exception message
- audit detail 원문
- Controller debug log

현재 DB 스키마는 `invite_token` 원문 저장 구조이므로,
이번 MVP 구현에서는 해당 스키마를 따른다.
추후 운영 전환 시 토큰 해시 저장을 검토한다.

---

## 7. 초대 만료

`invite_expires_at`은 설정값으로 관리한다.

예:

```yaml
movi:
  guardian:
    invitation-expire-hours: 24
```

만료시간을 Java 코드에 하드코딩하지 않는다.

설정 추가 시:

```text
application.yml.example
application-local.yml.example
application-test.yml.example
```

관련 템플릿도 함께 갱신한다.

---

## 8. Response

### 성공

```json
{
  "success": true,
  "data": {
    "linkId": 15,
    "status": "REQUESTED",
    "guardianName": "김보호",
    "relation": "자녀",
    "inviteExpiresAt": "2026-08-18T17:00:00+09:00"
  },
  "voiceMessage": "보호자에게 연결 요청 문자를 보냈습니다."
}
```

응답에 다음 값은 포함하지 않는다.

```text
guardianPhone 원문
guardianPhone 암호문
inviteToken
```

DTO는 정적 팩토리 메서드를 사용한다.

```java
GuardianLinkRequestResponse.from(...)
```

---

## 9. SMS payload

Notification Service에 전달할 내부 모델 예시:

```json
{
  "notificationId": 101,
  "channel": "SMS",
  "templateCode": "GUARDIAN_INVITE",
  "targetPhone": "<복호화된 전송 대상 번호>",
  "variables": {
    "protecteeName": "홍길동",
    "guardianName": "김보호",
    "inviteUrl": "https://<frontend-domain>/guardian/invite?token=..."
  }
}
```

전화번호는 Notification Provider 호출 직전에만 필요한 범위에서 복호화한다.

Provider에 넘긴 전화번호를 로그에 출력하지 않는다.

---

## 10. 자기 자신 등록 방지

요청한 보호자 전화번호가 현재 사용자의 전화번호와 동일하면 거부한다.

비교는 암호문을 직접 비교하지 않는다.

현재 사용자의 중복 확인용 `users.phone_hash`와
정규화된 보호자 전화번호의 HMAC-SHA256 값을 비교한다.

```text
currentUser.phoneHash == hash(normalizedGuardianPhone)
    -> 자기 자신 등록 요청
```

---

## 11. 7.7 중복 방지와 연계

등록 요청 시 다음 케이스를 막는다.

### 보호자가 이미 가입되어 연결되어 있는 경우

```text
protectee_user_id = 현재 사용자
guardian_user_id = 해당 보호자
status IN (REQUESTED, ACTIVE)
```

### 가입 전 동일 전화번호로 다시 요청하는 경우

현재 `guardian_links`에는 `guardian_phone_hash` 컬럼이 없다.

AES가 랜덤 IV를 사용하는 정상적인 암호화 방식이면
`guardian_phone` 암호문 비교만으로 중복을 안전하게 판별할 수 없다.

따라서 이 케이스는 별도의 기능명세
`07-04-07-guardian-relation-duplicate-prevention.md`의
Specification Gap을 따른다.

---

## 12. 예외

사용할 ErrorCode는 먼저 기존 `global/error/ErrorCode`를 검색한다.

필요한 의미:

| 상황 | HTTP 권장 | 사용자 음성 메시지 |
|---|---:|---|
| 입력값 오류 | 400 | "보호자 정보를 다시 확인해 주세요." |
| 자기 자신 등록 | 400 | "본인은 보호자로 등록할 수 없습니다." |
| 이미 연결됨 | 409 | "이미 연결된 보호자입니다." |
| 동일 요청 존재 | 409 | "이미 보호자 연결을 요청했습니다." |
| SMS 발송 장애 | 정책 확정 필요 | 기술 오류를 그대로 읽지 않음 |

새 ErrorCode가 필요하면 `message`, `voiceMessage`를 모두 정의하고
`docs/error-codes.md`를 갱신한다.

---

## 13. Service 책임

권장 클래스 구성:

```text
domain/guardian/
├── controller/
│   └── GuardianLinkController
├── application/
│   ├── GuardianLinkService
│   └── GuardianInvitationService
├── dto/
│   ├── request/GuardianLinkRequest
│   └── response/GuardianLinkRequestResponse
└── repository/
    └── GuardianLinkRepository

domain/notification/
└── application/
    └── NotificationService
```

기존 클래스가 있다면 새로 만들지 않고 기존 구조를 확장한다.

Controller가 Repository 또는 SMS Provider를 직접 호출하지 않는다.

---

## 14. 필수 테스트

### GuardianLinkService

```text
보호자_등록을_요청하면_REQUESTED_연결이_생성된다
보호자_등록을_요청하면_SMS_알림_이력이_생성된다
자기_전화번호를_보호자로_입력하면_거부한다
이미_ACTIVE인_보호자를_다시_등록하면_거부한다
이미_REQUESTED인_연결을_다시_요청하면_거부한다
```

### SMS

```text
연결_요청이_생성되면_GUARDIAN_INVITE_SMS를_발송한다
SMS_발송에_성공하면_알림_상태를_SENT로_변경한다
SMS_발송에_실패하면_알림_상태를_FAILED로_변경한다
SMS_로그에_보호자_전화번호를_남기지_않는다
```

테스트는 AAA 패턴과 BDDMockito를 사용한다.

---

## 15. 완료 조건

- [ ] 현재 사용자를 `@CurrentUser`로 식별한다.
- [ ] 전화번호를 정규화한다.
- [ ] 자기 자신 등록을 차단한다.
- [ ] 중복 연결을 검증한다.
- [ ] `guardian_links`가 `REQUESTED`로 생성된다.
- [ ] `invite_token`과 만료시각이 생성된다.
- [ ] 전화번호를 AES 암호화해 저장한다.
- [ ] `notifications`에 `GUARDIAN_INVITE`가 생성된다.
- [ ] SMS Provider 연동 또는 Mock 연동이 완료된다.
- [ ] 민감정보가 로그에 노출되지 않는다.
- [ ] 성공 응답에 `voiceMessage`가 존재한다.
- [ ] 단위 테스트가 통과한다.
- [ ] `./gradlew build`가 통과한다.
