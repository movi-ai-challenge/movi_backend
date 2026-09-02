# guardian 도메인

보호자 연결과 위험 거래 통보를 다룬다. FDS가 MEDIUM·HIGH로 판정한 이체가 여기로 흘러와 보호자 문자로 나간다.

도메인 전반의 불변식은 [docs/domain-guide.md](../../../../../../../docs/domain-guide.md)가 기준이다. 이 문서는 패키지 내부 관점에서 보충한다.

## 축이 둘이다

| | 연결 | 통보 |
|---|---|---|
| 테이블 | `guardian_links` | `notifications` |
| 진입점 | `GuardianLinkService` | `GuardianRiskAlertAdapter` |
| 부르는 쪽 | 사용자 (REST) | `transfer` 도메인 (FDS 판정 직후) |

**보호자에게 이체 승인 권한은 없다.** MVP에서 사전 차단은 제외했고 알림만 받는다.

## 보호자 등록은 초대 없이 즉시 ACTIVE 다

`GuardianLinkService.register()`는 이름·전화번호를 받아 그 자리에서 `activateWithoutInvite()`로 `ACTIVE`를 만든다. 보호자가 Movi 회원일 필요가 없다 — 알림은 전화번호로 나가기 때문이다.

> `docs/domain-guide.md`는 아직 "보호자 연결(SMS 초대 → 수락)"으로 적고 있다. 초대 흐름은 코드에서 빠졌고 `GuardianLink.accept()`만 남아 있다. **문서 쪽이 낡았다.**

초대가 없어졌는데도 `invite_token`(NOT NULL·UNIQUE)과 `invite_expires_at`(NOT NULL)은 스키마에 남아 있다. 그래서 등록할 때마다 쓰이지 않을 난수 토큰을 만들어 채우고, 만료 시각은 등록 시각으로 둔다 — 훗날 초대가 되살아나도 이 토큰들이 이미 만료된 것으로 취급되게 하려는 것이다. **이 두 컬럼을 지우기 전까지 값 채우기를 생략하면 등록이 실패한다.**

### 중복 검사가 두 가지 방식인 이유

`guardian_phone`은 AES 암호문이고 무작위 IV를 쓰므로 **같은 번호도 암호문이 매번 다르다.** 암호문끼리 비교하면 중복을 못 잡는다.

- **자기 자신 등록 차단** — `users.phone_hash`(HMAC)와 비교한다. 카카오 가입 직후에는 `phoneHash`가 비어 있을 수 있고, 그때는 비교 대상이 없어 통과시킨다.
- **같은 보호자 중복 등록 차단** — `guardian_links`에는 검색용 해시 컬럼이 없다. 활성 링크만 꺼내 하나씩 복호화해 비교한다. 한 사람의 보호자는 많아야 몇 명이라 감당되는 방식이다. 보호자 수가 늘어날 일이 생기면 해시 컬럼을 먼저 추가해야 한다.

해제(`REVOKED`)된 번호는 중복으로 보지 않는다. 다시 등록할 수 있어야 하기 때문이다.

## 통보는 이체 커밋 뒤에 나간다

`GuardianRiskAlertAdapter`가 `TransferRiskAlertPort` 구현으로 `transfer` 도메인에 꽂힌다. MEDIUM·HIGH가 아니면 아무것도 하지 않는다.

```text
FDS 판정 → (MEDIUM|HIGH) → afterCommit 등록 → 이체 트랜잭션 커밋
                                             → queue()  : notifications 저장 (REQUIRES_NEW)
                                             → send()   : 제공자 호출
                                             → markSent() | markFailed() (각각 REQUIRES_NEW)
```

**커밋 뒤에 보내는 것이 핵심이다.** 트랜잭션 안에서 보내면 이후 롤백된 이체에 대해 "이체가 완료됐다"는 문자가 이미 나가 있게 된다. 트랜잭션이 없는 호출 경로도 있어서 어댑터가 동기화 활성 여부를 확인하고, 없으면 그냥 즉시 보낸다.

`queue()`·`markSent()`·`markFailed()`가 전부 `REQUIRES_NEW`인 것도 같은 이유다. 알림 기록이 호출부 트랜잭션에 묶이면 안 된다.

### 알림 실패가 이체를 무너뜨리지 않는다

`GuardianRiskAlertDeliveryService`는 모든 단계에서 `RuntimeException`을 잡아 로그만 남기고 넘어간다. **이체는 이미 끝났고, 통보가 안 됐다고 되돌릴 수 없다.** FDS 평가 실패가 이체를 막는 fail-closed와는 방향이 반대다 — 다루는 대상이 다르기 때문이다.

발송은 성공했는데 `markSent()` 저장이 실패하면 FAILED로 덮어쓰지 않는다. 덮어쓰면 재시도가 돌아 같은 문자가 한 번 더 나간다.

## 재시도 상태 기계

`NotificationStatus`는 셋인데 **`QUEUED`가 두 가지 뜻으로 쓰인다** — 아직 안 보낸 것과, 실패해서 다시 보낼 것.

```text
저장            → QUEUED, nextRetryAt=null
발송 성공        → SENT,   nextRetryAt=null, providerMsgId 기록
발송 실패        → retryCount++
                  retryCount <  maxAttempts → QUEUED, nextRetryAt=now+delay
                  retryCount >= maxAttempts → FAILED, nextRetryAt=null   (포기)
```

**`FAILED`는 "실패했다"가 아니라 "포기했다"는 뜻이다.** 재시도가 남아 있는 실패는 `QUEUED`로 되돌아간다. 상태만 보고 판단하면 거꾸로 읽게 되므로 `retryCount`·`nextRetryAt`을 같이 봐야 한다.

`findDueRetries()`는 `status=QUEUED and nextRetryAt <= now`로 고른다. 방금 저장된 알림은 `nextRetryAt`이 null이라 여기 걸리지 않는다 — 최초 발송은 `deliver()`가 직접 하고, 스케줄러는 한 번 이상 실패한 것만 집는다. 이 null을 채우면 같은 알림이 두 경로에서 동시에 나간다.

스케줄러는 `movi.notification.retry.scan-interval`(기본 30초)마다 돌고, `enabled`는 `matchIfMissing = true`라 설정이 없으면 켜진 상태다.

## 발송 대역이 셋이다

`SmsNotificationSender` 포트 하나에 구현이 셋이고, 프로필과 `movi.sms.provider`로 갈린다.

| 구현 | 조건 | 동작 |
|---|---|---|
| `MockSmsNotificationSender` | `local`·`test` | 보내지 않고 **본문을 로그로 남긴다** |
| `UnavailableSmsNotificationSender` | 운영 + `provider=none` | 보내지 않는다 |
| `SolapiSmsNotificationSender` | 운영 + `provider=solapi` | **실제 문자가 나간다** |

Mock이 본문을 남기는 이유는 이 문구가 보호자 폰에 그대로 뜨고 앱에서는 TTS로도 읽히기 때문이다. 발송 여부만이 아니라 무엇이 나가는지가 검증 대상이다. 전화번호는 암호문이라도 로그에 남기지 않는다.

솔라피 구현은 **실패를 예외로 올린다.** 조용히 삼키면 호출부가 FAILED를 기록하지 못해 재시도 자체가 일어나지 않는다.

실발송 점검은 `SolapiLiveSendTest`가 맡는다. `SOLAPI_LIVE_TEST=true`가 있을 때만 돌고 인증정보도 전부 환경변수로 받는다 — 평소 스위트에서 돌면 매번 문자가 나가고 비용이 든다.

## 알림 조회의 소유권은 두 방향이다

`NotificationRepository.findMine()`은 **내 이체 때문에 나간 알림**(`guardianLink.protecteeUser`)과 **내가 보호자로 받은 알림**(`notification.user`)을 함께 본다.

수신자 하나로 거르면 안 된다. **`notification.user`는 미가입 보호자면 null이다** — 초대를 수락해야 계정이 연결되는데, 지금은 초대 흐름 자체가 없어 사실상 대부분의 알림이 여기 해당한다. 수신자 기준으로만 조회하면 정작 발송을 확인해야 할 알림이 통째로 빠진다.

## 변경 이력

- **2026-09-02** — 알림 발송 확인 수단을 추가했다. 발송 여부를 확인할 방법이 제공자 콘솔과 DB뿐이라, 실제 위험 이체를 태우고도 문자가 나갔는지 코드 쪽에서 알 수 없었다. `GET /api/v1/notifications`로 `status`·`providerMsgId`·`retryCount`·`nextRetryAt`을 함께 내리고, `MockSmsNotificationSender`가 본문을 로그로 남기게 했다. 소유권 기준을 두 방향으로 둔 것도 이때다.
- **이전** — 보호자 등록을 초대 방식에서 즉시 등록으로 바꾸고, SMS 제공자로 솔라피를 연동했다.
