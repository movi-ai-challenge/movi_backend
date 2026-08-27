# transfer 도메인

이체 실행과 거래내역을 다룬다. **이 패키지에서 실제로 돈이 움직인다.**

도메인 전반의 불변식은 [docs/domain-guide.md](../../../../../../../docs/domain-guide.md), 파트 간 계약은 [docs/integration-spec.md](../../../../../../../docs/integration-spec.md)가 기준이다. 이 문서는 패키지 내부 관점의 보충 설명이다.

## 책임

| 클래스 | 역할 |
|---|---|
| `TransferValidationService` | 금액·수취인·한도 검증. 누락 시 재질문 정보 반환 |
| `TransferTargetResolver` | 출금 계좌·수취인 소유권 확인 (음성·직접 입력 공용) |
| `TransferExecutionService` | 상태 전이, FDS 평가, 오픈뱅킹 호출, 멱등성 |
| `DirectTransferService` | 화면 직접 입력 송금의 검토·실행 |
| `TransferConfirmationStore` | 직접 입력 검토 스냅샷과 확인 ID |
| `TransferRecipientQueryService` | 등록 수취인 목록 |
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

### 송금 경로는 둘, 검증은 하나

음성 확인 발화와 화면 직접 입력이 모두 송금을 시작할 수 있다. 음성을 쓸 수 없는 상황(마이크 거부, 조용한 장소, 인식 반복 실패)에서도 송금을 끝낼 수 있어야 하기 때문이다. **접근성이 이 제품의 존재 이유인 이상 음성은 기본 경로이지 유일한 경로가 아니다.**

두 경로는 입력을 받는 방식만 다르고 `TransferExecutionService` 하나로 합쳐진다. 한도·잔액·소유권·FDS·멱등성이 경로마다 갈라지면, 한쪽만 고친 정책이 다른 쪽에서 뚫린다.

```text
음성   : 확인 발화 + confirmationId + idempotencyKey → voice_sessions.pending_slots 스냅샷
직접   : POST /api/transfers/review → confirmationId → POST /api/transfers + idempotencyKey
                                     ↓
                        TransferExecutionService (공통)
```

직접 입력에서도 **금액·수취인·출금 계좌는 실행 요청이 아니라 서버 스냅샷에서 읽는다.** 실행 요청이 금액을 다시 실어 보낼 수 있으면, 사용자가 검토한 내용과 다른 금액이 나갈 수 있다.

수취인은 **등록된 수취인 ID로만** 지정한다. 이름이나 계좌번호를 직접 받으면 프런트가 수취인을 만들어 내는 셈이고, 오타 한 번이 모르는 계좌로 가는 이체가 된다. 음성에서 새 계좌번호 발화를 막는 것과 같은 이유다.

확인 하나는 멱등성 키 하나에만 묶인다. 실행 버튼을 두 번 눌러 키가 새로 만들어지면 두 번째는 `TRANSFER_4007`로 거부된다. 반대로 **같은 키의 재시도는 통과**시킨다 — 응답을 받지 못한 사용자의 복구 경로이기 때문이다. 그래서 실행 API는 멱등성 조회를 확인 검증보다 **먼저** 한다.

`TransferConfirmationStore`는 `OAuthStateStore`·`LoginHandoffStore`와 같은 이유로 메모리에 둔다. 서버가 여러 대가 되면 검토를 받은 서버와 실행을 받는 서버가 달라져 실패하므로 그때는 공유 저장소로 옮긴다. 재기동으로 확인이 사라지면 사용자는 검토를 다시 하는데, 돈이 나간 뒤가 아니므로 안전한 쪽으로 실패한다.

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

## 확인 필요

직접 입력 송금은 FDS에 `sttConfidence=1.0`으로 전달된다. 화면 입력에는 인식 오류가 없기 때문인데, **모델 입장에서 음성 송금과 화면 송금은 위험 성격이 다르다.** 경로를 구분하는 피처(`channel` 등) 추가는 AI 파트와 계약을 확정한 뒤 별도 변경으로 처리한다.

## 변경 이력

- **2026-08-25** — 거래내역 단건 상세 조회와 음성 안내 추가 (#71)
  - `GET /api/transactions/{transactionId}` 신설, `TransactionDetailResponse` 추가
  - 목록·상세 응답에 `voiceMessage` 채움 (기존 목록 응답에는 없었다)
  - `ErrorCode.TRANSACTION_NOT_FOUND`(TRANSFER_4042) 추가

- **2026-08-28** — 직접 입력 송금 검토·실행과 등록 수취인 목록 추가
  - `GET /api/transfers/recipients`, `POST /api/transfers/review`, `POST /api/transfers` 신설
  - `TransferConfirmationStore`로 검토 스냅샷을 서버가 소유. 실행 요청은 확인 ID와 멱등성 키만 받는다
  - 출금 계좌·수취인 소유권 조회를 `TransferTargetResolver`로 모아 음성 경로와 공유
  - `ErrorCode.CONFIRMATION_INVALID`(TRANSFER_4007) 추가
  - `movi.transfer.confirmation-expire-minutes`(기본 5분) 추가 — 화면 검토는 음성 확인 60초보다 길다
- **2026-08-28** — 직접 입력 실행 요청이 `deviceUuid`를 받아 FDS 신뢰 기기 피처로 넘긴다 (#96). 음성 세션과 달리 기기를 들고 있을 세션이 없어 실행 요청이 직접 실어 보낸다.
