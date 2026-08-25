# 7.1 보호자 등록

버전: `v2.0`
대상: Spring Backend
기능 ID: `7.1 보호자 등록`
연계 기능: `보호자 SMS 통보`

---

## 1. 목적

이용자가 회원가입 온보딩에서 보호자의 이름·전화번호·관계를 입력하면, **확인 절차 없이 즉시**
보호자 연결이 성립한다.

이전 버전(`v1.0`)은 등록 요청 → SMS 초대 → 보호자가 초대 링크로 확인·승인하는 흐름이었다.
초대 확인용 프론트 화면을 따로 만들지 않기로 하면서 이 흐름을 제거했다. 이 문서는 그 v2 설계다.
초대·승인·거절 흐름을 다루던 `07-02-03-guardian-request-approval.md`는 더 이상 유효하지 않아
삭제했다.

보호자는 Movi 회원이 아니어도 된다. 전화번호만으로 연결되고, 알림도 전화번호로 나간다.

---

## 2. 관련 테이블

### guardian_links

사용 컬럼:

| 컬럼 | 용도 |
|---|---|
| `link_id` | 보호자 연결 식별자 |
| `protectee_user_id` | 피보호자(회원가입한 본인) |
| `guardian_user_id` | 보호자가 Movi 회원이면 바인딩. 아니면 `NULL` (이 흐름에서는 항상 `NULL`로 생성된다) |
| `guardian_name` | 보호자 이름 |
| `guardian_phone` | AES 암호화한 보호자 전화번호 |
| `guardian_phone_hash` | 중복 확인용 HMAC-SHA256 |
| `relation` | 보호자와의 관계 |
| `status` | 생성 즉시 `ACTIVE` |
| `permission_scope` | 보호자 권한 범위 |
| `linked_at` | 연결 생성 시각 |

### notifications

등록 통보 SMS 발송 이력을 저장한다.

```text
channel       = SMS
template_code = GUARDIAN_LINK_REGISTERED
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

현재 사용자는 `@CurrentUser AuthUser`로 받는다. `protecteeUserId`를 Request Body로 받지 않는다.

카카오 로그인 콜백 자체에는 입력 폼이 없어(순수 OAuth 리다이렉트) 이 API는 로그인 직후
온보딩 화면에서 별도로 호출한다. PIN 등록(`POST /api/v1/auth/pin/register`)과 같은 패턴이다.

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
| `relation` | string | 예 | `GuardianRelation` 값으로 변환 |

전화번호는 저장 전에 정규화한다. `010-1234-5678` / `010 1234 5678` / `01012345678`
모두 `01012345678`로 통일한다. DB에는 원문을 저장하지 않는다.

---

## 5. 처리 순서

```text
1. JWT 인증
2. 현재 사용자(피보호자) 조회
3. 요청 DTO 검증
4. 보호자 전화번호 정규화 + 해시
5. 자기 자신을 보호자로 등록하는지 검증
6. 동일 전화번호로 이미 ACTIVE인 연결이 있는지 검증
7. guardian_links를 ACTIVE로 즉시 생성
8. notifications QUEUED 생성 + 등록 통보 SMS 발송
9. 발송 성공 시 SENT, 실패 시 FAILED
10. ApiResponse 반환
```

연결 생성과 알림 이력 생성은 한 트랜잭션에서 처리한다.
SMS 발송 실패는 이미 성립한 연결을 취소하지 않는다 — 연결은 이미 끝났고, 실패했다는 사실만
응답 문구로 안내한다.

---

## 6. Response

### 성공

```json
{
  "success": true,
  "data": {
    "linkId": 15,
    "status": "ACTIVE",
    "guardianName": "김보호",
    "relation": "자녀",
    "notificationSent": true
  },
  "voiceMessage": "김보호 님을 보호자로 등록했어요. 이상 거래가 감지되면 문자로 알려드릴게요."
}
```

응답에 `guardianPhone` 원문·암호문은 포함하지 않는다.

DTO는 정적 팩토리 메서드를 사용한다.

```java
GuardianLinkRegisterResponse.from(...)
```

---

## 7. SMS payload

```json
{
  "notificationId": 101,
  "channel": "SMS",
  "templateCode": "GUARDIAN_LINK_REGISTERED",
  "targetPhone": "<복호화된 전송 대상 번호>",
  "variables": {
    "protecteeName": "홍길동"
  }
}
```

전화번호는 Notification Provider 호출 직전에만 필요한 범위에서 복호화한다.
Provider에 넘긴 전화번호를 로그에 출력하지 않는다.

---

## 8. 자기 자신 등록 방지

요청한 보호자 전화번호가 현재 사용자의 전화번호와 동일하면 거부한다. 암호문을 직접 비교하지
않고, `users.phone_hash`와 정규화된 보호자 전화번호의 HMAC-SHA256 값을 비교한다.

```text
currentUser.phoneHash == hash(normalizedGuardianPhone)
    -> 자기 자신 등록 요청
```

---

## 9. 7.7 중복 방지와 연계

같은 피보호자-보호자(전화번호 기준) 쌍이 이미 `ACTIVE`면 거부한다.
`REVOKED`(해제됨)는 중복으로 보지 않는다 — 해제한 뒤 다시 등록할 수 있어야 한다.
자세한 내용은 `07-04-07-guardian-relation-duplicate-prevention.md` 참조.

---

## 10. 예외

| 상황 | ErrorCode | HTTP | 사용자 음성 메시지 |
|---|---|---:|---|
| 입력값 오류 | 검증 실패(400) | 400 | "보호자 정보를 다시 확인해 주세요." |
| 자기 자신 등록 | `SELF_LINK_NOT_ALLOWED` | 400 | "본인은 보호자로 등록할 수 없어요." |
| 이미 연결됨 | `ALREADY_LINKED` | 400 | "이미 연결된 분이에요." |
| 허용되지 않은 관계값 | `INVALID_GUARDIAN_RELATION` | 400 | "보호자 관계 정보를 다시 확인해 주세요." |

---

## 11. Service 책임

```text
domain/guardian/
├── controller/GuardianLinkController
├── application/GuardianLinkService
├── dto/
│   ├── request/GuardianLinkCreateRequest
│   └── response/GuardianLinkRegisterResponse
├── repository/GuardianLinkRepository
└── entity/GuardianLink

domain/notification/
└── application/NotificationService
```

Controller가 Repository 또는 SMS Provider를 직접 호출하지 않는다.

---

## 12. 완료 조건

- [x] 현재 사용자를 `@CurrentUser`로 식별한다.
- [x] 전화번호를 정규화한다.
- [x] 자기 자신 등록을 차단한다.
- [x] 중복 연결(ACTIVE)을 검증한다.
- [x] `guardian_links`가 확인 절차 없이 `ACTIVE`로 생성된다.
- [x] 전화번호를 AES 암호화해 저장한다.
- [x] `notifications`에 `GUARDIAN_LINK_REGISTERED`가 생성된다.
- [x] SMS Provider 연동(Mock) 완료.
- [x] 민감정보가 로그에 노출되지 않는다.
- [x] 성공 응답에 `voiceMessage`가 존재한다.
- [x] 단위 테스트가 통과한다.
- [x] `./gradlew build`가 통과한다.
