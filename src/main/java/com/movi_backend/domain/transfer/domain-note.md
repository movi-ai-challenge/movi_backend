# transfer 도메인

이체 실행과 거래내역을 다룬다. **이 패키지에서 실제로 돈이 움직인다.**

도메인 전반의 불변식은 [docs/domain-guide.md](../../../../../../docs/domain-guide.md), 파트 간 계약은 [docs/integration-spec.md](../../../../../../docs/integration-spec.md)가 기준이다. 이 문서는 패키지 내부 관점의 보충 설명이다.

## 책임

| 클래스 | 역할 |
|---|---|
| `TransferValidationService` | 금액·수취인·한도 검증. 누락 시 재질문 정보 반환 |
| `TransferExecutionService` | 상태 전이, FDS 평가, 오픈뱅킹 호출, 멱등성 |
| `TransferQueryService` | 멱등성 키로 이체 상태 복구 |
| `TransactionQueryService` | 거래내역 목록·단건 조회 |

## 지켜야 할 것

### 상태는 되돌아가지 않는다

```text
PENDING → RISK_REVIEW → COMPLETED
                      → BLOCKED
        → FAILED / CANCELED
```

`COMPLETED` 이후에는 어떤 상태로도 전이하지 않는다. 모든 이체는 FDS 평가를 거치며, 평가 없이 `COMPLETED`가 될 수 없다.

### 멱등성은 두 겹으로 막는다

음성은 오인식·중복 발화가 잦고 모바일 네트워크는 재시도가 흔하다. 애플리케이션 조회만으로는 동시 요청을 막을 수 없어 **사용자 행 비관적 잠금 + `(user_id, idempotency_key)` UNIQUE**를 함께 쓴다. 같은 키의 재요청은 새 이체를 만들지 않고 기존 결과를 돌려준다.

### FDS는 Fail-Closed

평가 실패를 저위험으로 간주하면 장애가 곧 보안 우회로가 된다. 타임아웃·통신 오류·역직렬화 실패·정의되지 않은 risk/decision 조합은 전부 **오픈뱅킹 이체를 호출하지 않는다.**

### 계좌번호는 필요한 순간에만 평문이 된다

`transfers.to_account_num`은 암호화 저장이고, 외부 이체 요청을 만들 때만 복호화한다. 복호화가 실패하면 외부 API를 호출하지 않는다.

**거래내역 응답에는 상대방 계좌번호를 넣지 않는다.** `TransactionResponse`·`TransactionDetailResponse` 모두 해당한다. 음성으로 계좌번호를 읽어 주면 사용자에게 쓸모가 없으면서 주변 사람에게 들린다.

### 조회 응답도 음성으로 읽힌다

거래내역은 화면 없는 사용자가 듣고 이해해야 하는 대표적인 응답이다. 목록·상세 모두 `voiceMessage`를 채우고, 금액은 반드시 `KoreanMoneyFormatter`를 거친다 — TTS가 `53000원`을 어떻게 읽을지 보장할 수 없다.

목록은 **건수만** 알린다. 스무 건을 끝까지 읽어 주면 듣는 사람이 따라올 수 없어, 개별 거래는 상세 조회로 넘긴다.

### 거래 조회는 계좌 소유자까지 확인한다

거래는 계좌에, 계좌는 사용자에 매달려 있다. 거래 ID만으로 조회하면 남의 거래가 열리므로 `transaction.account.user.id`를 검증한다. 없는 거래와 남의 거래는 **같은 응답**(`TRANSACTION_NOT_FOUND`)을 준다 — 구분해서 알려주면 ID를 훑어 남의 거래 존재 여부를 알아낼 수 있다.

## 변경 이력

- **2026-08-25** — 거래내역 단건 상세 조회와 음성 안내 추가 (#71)
  - `GET /api/transactions/{transactionId}` 신설, `TransactionDetailResponse` 추가
  - 목록·상세 응답에 `voiceMessage` 채움 (기존 목록 응답에는 없었다)
  - `ErrorCode.TRANSACTION_NOT_FOUND`(TRANSFER_4042) 추가
