# 7.4 관계 정보 · 7.7 중복 연결 방지

버전: `v1.0`  
대상: Spring Backend  
기능 ID: `7.4 관계 정보 입력`, `7.7 중복 연결 방지`

---

## 1. 목적

보호자 연결 관계의 의미를 `relation`에 저장하고,
동일한 이용자와 보호자가 중복해서 연결되거나
동일 보호자에게 중복 초대가 생성되는 것을 차단한다.

관련 테이블:

```text
guardian_links
```

---

## 2. 관계 정보

사용 컬럼:

```text
guardian_links.relation VARCHAR(30)
```

기능명세의 예:

```text
자녀
배우자
사회복지사
```

현재 DB가 ENUM이 아니라 VARCHAR이므로
백엔드에서 허용값을 enum으로 관리하는 것을 권장한다.

예:

```java
public enum GuardianRelation {
    CHILD,
    SPOUSE,
    SOCIAL_WORKER,
    OTHER
}
```

단, 실제 기존 `domain/guardian/type/`에 관련 enum이 이미 있는지 먼저 확인한다.
있다면 새 enum을 만들지 않는다.

프론트 표시용 한국어 이름과 DB 저장값을 분리할 수 있다.

---

## 3. 관계 입력 시점

MVP 기본 흐름:

```text
이용자
  -> 보호자 등록 요청
  -> relation 입력
  -> guardian_links.relation 저장
  -> 보호자 SMS
  -> 보호자 확인
  -> 연결 승인
```

즉, 관계는 7.1 등록 요청 시 입력하고
7.3 승인 후에도 동일한 관계값을 유지한다.

보호자가 관계를 임의로 변경하지 않는다.

---

## 4. 관계 검증

Request 예:

```json
{
  "guardianName": "김보호",
  "guardianPhone": "01012345678",
  "relation": "CHILD"
}
```

검증:

```text
- null 금지
- 빈 문자열 금지
- 허용된 관계값만 허용
```

`OTHER`를 지원한다면 별도 relationDetail 컬럼이 현재 스키마에 없으므로
상세 자유입력 기능은 임의로 추가하지 않는다.

---

## 5. 중복의 정의

### Case A. 이미 활성 관계

다음 관계가 존재하면 새로운 연결을 생성하지 않는다.

```text
protectee_user_id = 현재 사용자
guardian_user_id  = 동일 보호자
status            = ACTIVE
```

### Case B. 처리 중인 동일 요청

다음 요청이 있으면 새로운 초대를 생성하지 않는다.

```text
protectee_user_id = 현재 사용자
동일한 보호자 전화번호
status            = REQUESTED
invite_expires_at > now
```

### Case C. 만료된 REQUESTED 요청

초대가 만료되었다면 새 요청을 허용할 수 있다.

기존 행을 덮어쓸지 새 행을 생성할지는 명세가 고정하지 않았으므로
MVP에서는 **새 행 생성**을 권장한다.

이력 추적이 쉬우며 기존 요청을 보존할 수 있다.

### Case D. REJECTED / REVOKED

과거 거절 또는 해제된 관계에 대한 재요청 허용 여부는
프로젝트 정책이 필요하다.

기본 권장:

```text
REJECTED -> 재요청 허용
REVOKED  -> 재요청 허용
```

단, 최종 정책이 다르면 기능명세를 먼저 수정한다.

---

## 6. 현재 스키마의 중복 검증 한계

현재 `guardian_links`:

```text
guardian_phone VARCHAR(255)  -- AES 암호화
```

만 존재한다.

정상적인 AES 암호화가 랜덤 IV를 사용하면:

```text
같은 전화번호
    ↓
암호화할 때마다 다른 암호문 가능
```

따라서 다음과 같은 비교는 하면 안 된다.

```java
guardianLinkRepository.existsByGuardianPhone(encryptedPhone);
```

암호화 결과가 같은지 비교해서 중복을 판별하는 설계는 안전하지 않다.

---

## 7. 권장 스키마 보완

7.7 중복 방지를 정확하게 구현하려면
전화번호 중복 검색 전용 HMAC 컬럼을 추가하는 것이 가장 일관적이다.

`users.phone_hash`와 동일한 원칙을 적용한다.

권장 컬럼:

```sql
ALTER TABLE guardian_links
ADD COLUMN guardian_phone_hash VARCHAR(64) NULL
COMMENT '보호자 전화번호 중복 확인용 HMAC-SHA256';

CREATE INDEX idx_glink_protectee_phone_status
ON guardian_links (
    protectee_user_id,
    guardian_phone_hash,
    status
);
```

저장:

```text
guardian_phone      = AES(normalizedPhone)
guardian_phone_hash = HMAC_SHA256(normalizedPhone)
```

조회:

```text
protectee_user_id
+ guardian_phone_hash
+ status
```

를 사용한다.

### 주의

스키마 변경을 실제 적용한다면 AGENTS.md 규칙에 따라 반드시 함께 수정한다.

```text
docs/schema.sql
docs/ERD.md
ERDCloud용 SQL
관련 JPA Entity
```

현재 `ddl-auto: validate`이므로 DB와 Entity가 불일치하면 애플리케이션 기동이 실패한다.

---

## 8. 중복 검증 Repository

스키마 보완 후 권장 쿼리 의미:

```text
exists active/requested guardian link
where
    protecteeUserId = ?
    guardianPhoneHash = ?
    status in (REQUESTED, ACTIVE)
```

Repository 메서드 예시:

```java
boolean existsByProtecteeUserIdAndGuardianPhoneHashAndStatusIn(
        Long protecteeUserId,
        String guardianPhoneHash,
        Collection<GuardianLinkStatus> statuses
);
```

정확한 네이밍은 기존 Repository 컨벤션을 따른다.

---

## 9. 동시 요청 방지

Application 레벨 `exists()` 검사만으로는 다음 경쟁 조건이 가능하다.

```text
Request A -> exists false
Request B -> exists false
Request A -> insert
Request B -> insert
```

해커톤 MVP라도 동일 사용자 요청이 동시에 들어갈 가능성이 있으므로
가능하면 DB 제약 또는 락을 고려한다.

단, MySQL에서 상태가 REQUESTED/ACTIVE일 때만 적용되는 partial unique index를
직접 만들 수 없으므로 단순 UNIQUE 제약으로 모든 이력을 제한하면
REJECTED/REVOKED 이후 재요청이 어려워진다.

따라서 MVP 권장:

```text
1. Service 중복 검사
2. 필요한 경우 protectee 단위 락 또는 트랜잭션
3. 승인 시 guardian_user_id 기준 재중복 검사
```

운영 단계에서는 별도 active-link 모델 또는 generated column 기반 제약을 검토한다.

---

## 10. 승인 시 2차 중복 검사

7.3 연결 승인 시에는 `guardian_user_id`가 확정되므로
다시 한 번 확인한다.

```text
protectee_user_id = link.protectee_user_id
guardian_user_id  = authUser.userId
status            = ACTIVE
```

이미 존재하면 승인하지 않는다.

이 검사는 7.1의 전화번호 기반 검사와 별개로 반드시 수행한다.

---

## 11. 에러

필요 의미:

```text
INVALID_GUARDIAN_RELATION
DUPLICATE_GUARDIAN_REQUEST
GUARDIAN_ALREADY_LINKED
```

기존 `ErrorCode` 59개를 먼저 검색한다.

없을 때만 추가하고:

```text
global/error/ErrorCode
docs/error-codes.md
```

를 함께 수정한다.

예시 voiceMessage:

```text
INVALID_GUARDIAN_RELATION
-> "보호자 관계 정보를 다시 확인해 주세요."

DUPLICATE_GUARDIAN_REQUEST
-> "이미 보호자 연결을 요청했습니다."

GUARDIAN_ALREADY_LINKED
-> "이미 연결된 보호자입니다."
```

---

## 12. 필수 테스트

```text
관계_정보를_입력하면_guardian_link에_저장된다
허용되지_않은_관계값은_거부한다

동일_전화번호의_REQUESTED_요청이_있으면_중복_요청을_거부한다
동일_보호자와_ACTIVE_관계가_있으면_중복_연결을_거부한다
만료된_REQUESTED_요청만_있으면_재요청할_수_있다
REJECTED_관계_재요청_정책을_명세대로_적용한다
REVOKED_관계_재요청_정책을_명세대로_적용한다

승인_시점에_동일_guardian_user_id_ACTIVE_관계가_있으면_거부한다
```

---

## 13. Specification Gap

현재 기능명세와 스키마만으로 최종 결정되지 않은 부분:

```text
1. relation의 최종 허용 enum 목록
2. REJECTED 이후 재요청 허용 여부
3. REVOKED 이후 재요청 허용 여부
4. guardian_phone_hash 컬럼 추가 여부
```

이 중 **guardian_phone_hash는 7.7 구현을 위해 추가하는 것을 권장**한다.

---

## 14. 완료 조건

- [ ] 관계값을 서버에서 검증한다.
- [ ] 관계값이 `guardian_links.relation`에 저장된다.
- [ ] 자기 자신 연결을 차단한다.
- [ ] REQUESTED 중복 요청을 차단한다.
- [ ] ACTIVE 중복 관계를 차단한다.
- [ ] 승인 시 guardian_user_id 기준으로 다시 중복 검사한다.
- [ ] AES 암호문 직접 비교로 중복을 판별하지 않는다.
- [ ] 스키마 변경 시 Entity/DDL/ERD를 함께 갱신한다.
- [ ] 관련 단위 테스트가 통과한다.
- [ ] `./gradlew build`가 통과한다.
