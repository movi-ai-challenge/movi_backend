# 7.4 관계 정보 · 7.7 중복 연결 방지

버전: `v2.0`
대상: Spring Backend
기능 ID: `7.4 관계 정보 입력`, `7.7 중복 연결 방지`

---

## 1. 목적

보호자 연결 관계의 의미를 `relation`에 저장하고, 동일한 이용자·보호자 쌍이 중복해서
연결되는 것을 차단한다.

`v1.0`은 등록 요청(`REQUESTED`) → 초대 → 승인(`ACTIVE`) 흐름을 전제로 "요청 중복"과
"활성 중복"을 따로 다뤘다. `07-01-guardian-registration-request.md` v2에서 확인 절차를
없애고 즉시 `ACTIVE`로 생성하는 방식으로 바뀌면서, 중복 정의도 하나로 단순해졌다.

관련 테이블: `guardian_links`

---

## 2. 관계 정보

```text
guardian_links.relation VARCHAR(30)
```

`domain/guardian/type/GuardianRelation`에 이미 정의되어 있다.

```java
public enum GuardianRelation {
    CHILD, SPOUSE, SOCIAL_WORKER, OTHER
}
```

프론트 표시용 한국어 이름(`displayNameOrNull`)과 DB 저장값(enum name)을 분리한다.
관계는 등록 시 입력한 값을 그대로 유지한다 — 이후 별도로 바꾸는 기능은 없다.

---

## 3. 중복의 정의

동일한 피보호자-보호자(전화번호 기준) 쌍이 이미 `ACTIVE`면 새로운 연결을 생성하지 않는다.

```text
protectee_user_id = 현재 사용자
guardian_phone_hash = HMAC(정규화된 보호자 전화번호)
status = ACTIVE
```

`REVOKED`(해제됨)는 중복으로 보지 않는다. 보호자를 해제한 뒤 같은 번호로 다시 등록할 수
있어야 한다.

`REQUESTED`/`REJECTED` 상태는 더 이상 존재하지 않는다 — 등록은 검증을 통과하면 즉시
`ACTIVE`가 되므로 "처리 중인 요청"이라는 중간 상태 자체가 없다.

---

## 4. 중복 검증이 암호문 비교로는 안 되는 이유

`guardian_phone`은 AES-GCM으로 저장하며 무작위 IV를 쓴다. 같은 전화번호도 암호화할 때마다
암호문이 달라지므로, 다음과 같은 비교는 항상 틀린 답을 낼 수 있다.

```java
// 하면 안 되는 방식
guardianLinkRepository.existsByGuardianPhone(encryptedPhone);
```

그래서 `users.phone_hash`와 같은 원칙으로 검색 전용 HMAC-SHA256 컬럼
`guardian_phone_hash`를 둔다. 저장은 암호문(`guardian_phone`)과 해시(`guardian_phone_hash`)를
같이 남기고, 조회는 해시로만 한다.

이 컬럼과 인덱스(`idx_glink_protectee_phone_status`)는 이미 스키마에 반영되어 있다
(`docs/migrations/20260819_add_guardian_links_phone_hash.sql`).

---

## 5. Repository

```java
boolean existsByProtecteeUserIdAndGuardianPhoneHashAndStatus(
        Long protecteeUserId,
        String guardianPhoneHash,
        GuardianLinkStatus status
);
```

`GuardianLinkService.validateNotDuplicated()`가 이 메서드로 `ACTIVE` 여부만 확인한다.
승인 단계가 없으므로 2차 중복 검사(과거 `guardian_user_id` 기준)도 필요 없다.

---

## 6. 동시 등록

같은 사용자가 동시에 같은 보호자 번호로 두 번 요청하면, `Service` 레벨의 `exists()` 검사만으로는
경쟁 조건이 발생할 수 있다(A가 확인 → B가 확인 → A 저장 → B 저장). MVP에서는 이 확률을
감수하고, 운영 단계에서 필요하면 `(protectee_user_id, guardian_phone_hash)` 부분 유니크
제약이나 애플리케이션 락을 추가로 검토한다. `REVOKED` 이후 재등록을 허용해야 하므로 단순
전체 UNIQUE 제약은 쓸 수 없다.

---

## 7. 자기 자신 등록 방지

`07-01-guardian-registration-request.md` §8 참조. 암호문이 아니라 `users.phone_hash`와
정규화된 보호자 전화번호의 HMAC을 비교한다.

---

## 8. 에러

```text
INVALID_GUARDIAN_RELATION  -> "보호자 관계 정보를 다시 확인해 주세요."
ALREADY_LINKED             -> "이미 연결된 분이에요."
SELF_LINK_NOT_ALLOWED      -> "본인은 보호자로 등록할 수 없어요."
```

`DUPLICATE_GUARDIAN_REQUEST`, `GUARDIAN_LINK_ALREADY_PROCESSED`는 요청/승인 중간 상태가
사라지면서 함께 제거했다.

---

## 9. 필수 테스트

```text
허용되지_않은_관계값은_거부한다
자기_전화번호를_보호자로_입력하면_거부한다
이미_ACTIVE인_보호자를_다시_등록하면_거부한다
```

`GuardianLinkServiceTest`에 구현되어 있다.

---

## 10. 완료 조건

- [x] 관계값을 서버에서 검증한다.
- [x] 관계값이 `guardian_links.relation`에 저장된다.
- [x] 자기 자신 연결을 차단한다.
- [x] ACTIVE 중복 관계를 차단한다.
- [x] AES 암호문 직접 비교로 중복을 판별하지 않는다.
- [x] 관련 단위 테스트가 통과한다.
- [x] `./gradlew build`가 통과한다.
