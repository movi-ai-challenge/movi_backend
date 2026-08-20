# 7.2 보호자 요청 확인 · 7.3 보호자 연결 승인

버전: `v1.0`  
대상: Spring Backend  
기능 ID: `7.2 보호자 요청 확인`, `7.3 보호자 연결 승인`

---

## 1. 목적

SMS 초대 링크를 받은 보호자가 연결 요청 내용을 확인하고,
로그인한 자신의 계정으로 연결을 승인한다.

승인 완료 시:

```text
guardian_links.guardian_user_id = 현재 보호자 userId
guardian_links.status           = ACTIVE
guardian_links.accepted_at      = 현재 시각
```

으로 변경한다.

이 기능의 "승인"은 **보호자 관계 연결 승인**이다.

거래 승인 기능이 아니다.

현재 MVP의 FDS 정책은:

```text
LOW    -> ALLOW
MEDIUM -> ALLOW_WITH_ALERT
HIGH   -> BLOCK
```

이며 Medium Risk 거래에 대한 보호자 거래 승인 절차는 구현하지 않는다.

---

## 2. 상태 흐름

```text
REQUESTED
   ├── 승인 -> ACTIVE
   ├── 거절 -> REJECTED
   └── 해제 -> 해당 없음

ACTIVE
   └── 향후 연결 해제 -> REVOKED
```

이번 구현 범위:

```text
REQUESTED -> ACTIVE
REQUESTED -> REJECTED (요청 확인 화면에서 거절까지 구현하는 경우)
```

허용하지 않는 예:

```text
ACTIVE -> ACTIVE 재승인
REJECTED -> ACTIVE 직접 변경
REVOKED -> ACTIVE 직접 변경
```

---

## 3. 7.2 요청 확인 Endpoint

초대 링크로 접근할 때 사용한다.

```http
GET /api/v1/guardian-links/invitations/{inviteToken}
```

### 처리 규칙

1. 토큰에 해당하는 `guardian_links`를 조회한다.
2. 존재하지 않으면 요청을 노출하지 않는다.
3. `status == REQUESTED`인지 확인한다.
4. `invite_expires_at > now`인지 확인한다.
5. 보호자에게 표시 가능한 정보만 반환한다.

### Response

```json
{
  "success": true,
  "data": {
    "linkId": 15,
    "protecteeName": "홍길동",
    "guardianName": "김보호",
    "relation": "자녀",
    "status": "REQUESTED",
    "expiresAt": "2026-08-18T17:00:00+09:00"
  },
  "voiceMessage": "홍길동 님이 보호자 연결을 요청했습니다."
}
```

다음 정보는 반환하지 않는다.

```text
피보호자 전화번호
보호자 전화번호 원문
AES 암호문
오픈뱅킹 정보
계좌번호
```

---

## 4. 7.3 연결 승인 Endpoint

```http
POST /api/v1/guardian-links/invitations/{inviteToken}/approve
Authorization: Bearer <JWT>
```

현재 로그인 사용자는 `@CurrentUser AuthUser`로 받는다.

Request Body는 없어도 된다.

---

## 5. 승인 사전조건

아래 조건을 모두 만족해야 한다.

```text
1. inviteToken이 존재한다.
2. link.status == REQUESTED
3. inviteExpiresAt이 현재 시각 이후다.
4. 현재 로그인 사용자가 존재하고 ACTIVE 상태다.
5. protecteeUserId와 현재 guardianUserId가 다르다.
6. 동일 피보호자-보호자 ACTIVE 관계가 존재하지 않는다.
7. 동일 요청이 이미 처리되지 않았다.
```

---

## 6. 승인 처리

```text
BEGIN TRANSACTION

1. guardian_links 요청 조회
2. REQUESTED 상태 검증
3. 토큰 만료 검증
4. 현재 사용자 검증
5. 자기 자신 연결 차단
6. 기존 ACTIVE 관계 중복 검사
7. guardian_user_id = authUser.userId
8. status = ACTIVE
9. accepted_at = now
10. 감사 로그 기록

COMMIT
```

동일한 초대 링크에 승인 요청이 동시에 들어와도
한 번만 성공해야 한다.

가능하면 조건부 UPDATE 또는 비관적 락을 사용한다.

예:

```sql
UPDATE guardian_links
SET guardian_user_id = ?,
    status = 'ACTIVE',
    accepted_at = CURRENT_TIMESTAMP
WHERE link_id = ?
  AND status = 'REQUESTED'
  AND invite_expires_at > CURRENT_TIMESTAMP;
```

영향받은 행이 `0`이면 현재 상태를 다시 확인하고
적절한 BusinessException으로 변환한다.

---

## 7. 승인 Response

```json
{
  "success": true,
  "data": {
    "linkId": 15,
    "protecteeUserId": 3,
    "status": "ACTIVE",
    "relation": "자녀",
    "acceptedAt": "2026-08-17T17:20:00+09:00"
  },
  "voiceMessage": "보호자 연결이 완료되었습니다."
}
```

응답 DTO는 `from()` 또는 `of()` 팩토리 메서드를 사용한다.

---

## 8. 거절 Endpoint

기능 범위에 포함할 경우:

```http
POST /api/v1/guardian-links/invitations/{inviteToken}/reject
Authorization: Bearer <JWT>
```

상태:

```text
REQUESTED -> REJECTED
```

Response:

```json
{
  "success": true,
  "data": {
    "linkId": 15,
    "status": "REJECTED"
  },
  "voiceMessage": "보호자 연결 요청을 거절했습니다."
}
```

---

## 9. 보호자 본인 확인에 대한 현재 범위

현재 스키마와 기능명세만으로 확정되는 것은:

```text
- 로그인한 사용자
- 유효한 invitation token
- guardian_links 연결 요청
```

이다.

SMS를 받은 전화번호와 로그인 사용자의 전화번호가 반드시 같은지
검증하는 강한 본인확인 방식은 현재 스키마만으로 완전히 정의되어 있지 않다.

### Specification Gap

```text
대상 기능:
7.2 / 7.3

불명확한 내용:
초대받은 전화번호의 실제 소유자임을 어떤 방식으로 검증할지

현재 구현 가능 범위:
유효한 invitation token + 로그인 사용자

선택지:
1. SMS OTP 추가
2. guardian_phone_hash 컬럼 추가 후 users.phone_hash 비교
3. Kakao 인증 프로필의 검증된 전화번호 사용

권장:
MVP에서는 invitation token + 로그인 계정을 사용하되,
운영 서비스 전환 전 전화번호 소유 검증을 추가한다.
```

임의로 OTP 기능을 추가하지 않는다.

---

## 10. 요청 목록

기능명세의 "보호자가 자신에게 도착한 연결 요청을 확인한다"를
일반적인 목록 API로 구현하려면 보호자 전화번호와 미가입 요청을
안전하게 매칭할 수 있어야 한다.

현재 `guardian_links.guardian_phone`은 AES 암호화 값이고
`guardian_phone_hash`가 존재하지 않는다.

따라서 **미승인 요청 목록을 로그인 사용자 전화번호로 찾는 API**는
현재 스키마만으로 효율적이고 안전하게 구현하기 어렵다.

이번 MVP에서는 SMS 초대 링크를 통한 단건 확인을 기본 흐름으로 한다.

`guardian_phone_hash` 컬럼이 추가되면 다음 API를 확장할 수 있다.

```http
GET /api/v1/guardian-links/requests
Authorization: Bearer <JWT>
```

---

## 11. 예외

필요 의미:

| 상황 | HTTP 권장 | voiceMessage 예시 |
|---|---:|---|
| 존재하지 않는 초대 | 404 | "보호자 연결 요청을 찾을 수 없습니다." |
| 만료된 초대 | 410 또는 프로젝트 정책 | "보호자 연결 요청이 만료되었습니다." |
| 이미 승인됨 | 409 | "이미 처리된 보호자 연결 요청입니다." |
| 이미 거절됨 | 409 | "이미 처리된 보호자 연결 요청입니다." |
| 자기 자신 연결 | 400 | "본인은 보호자로 연결할 수 없습니다." |
| 기존 관계 중복 | 409 | "이미 연결된 보호자 관계입니다." |

기존 `ErrorCode`를 먼저 검색하고 중복 코드를 만들지 않는다.

---

## 12. 감사 로그

연결 승인 시 기록:

```text
actor_type    = GUARDIAN
action        = GUARDIAN_LINK_APPROVED
resource_type = GUARDIAN_LINK
resource_id   = link_id
```

detail에는 다음 값을 넣지 않는다.

```text
inviteToken
guardianPhone
phoneHash
```

---

## 13. 필수 테스트

```text
유효한_초대_토큰으로_요청_내용을_조회한다
만료된_초대_토큰은_조회할_수_없다
REQUESTED_요청을_승인하면_ACTIVE로_변경된다
승인하면_guardian_user_id가_현재_사용자로_설정된다
승인하면_accepted_at이_기록된다
이미_ACTIVE인_요청을_다시_승인하면_거부한다
만료된_요청을_승인하면_거부한다
자기_자신의_보호자로_연결하면_거부한다
동일한_피보호자_보호자_관계가_있으면_승인을_거부한다
동일_요청에_동시에_승인하면_하나만_성공한다
```

시간 만료 테스트에서는 직접 `LocalDateTime.now()`를 여러 곳에서 호출하지 말고
프로젝트의 시간 추상화가 있다면 이를 사용한다.

---

## 14. 완료 조건

- [ ] 토큰 기반 요청 확인 API가 구현된다.
- [ ] REQUESTED 상태만 승인할 수 있다.
- [ ] 만료 토큰을 거부한다.
- [ ] 자기 자신 연결을 거부한다.
- [ ] 기존 ACTIVE 관계 중복을 거부한다.
- [ ] 승인 시 `guardian_user_id`가 바인딩된다.
- [ ] 승인 시 `status=ACTIVE`가 된다.
- [ ] 승인 시 `accepted_at`이 기록된다.
- [ ] 동시 승인에 안전하다.
- [ ] 민감정보를 응답·로그에 노출하지 않는다.
- [ ] `voiceMessage`를 제공한다.
- [ ] 단위 테스트가 통과한다.
- [ ] `./gradlew build`가 통과한다.
