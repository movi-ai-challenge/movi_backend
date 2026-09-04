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

- **2026-09-04** — **금액은 모델보다 발화 원문을 믿는다.** 운영에서 측정해 보니 `gpt-4o-mini` 가 "십만 이천원"을 4회 중 4회 120,000 으로 읽었다. 사용자가 말한 값은 102,000 이다. 모든 복합 금액이 무너지는 것은 아니어서("십이만 삼천원"은 정확) 어느 표현이 무너질지 미리 알 수 없다.
  - `SpokenAmountParser` 를 두고, 원문에서 읽어낸 값이 있으면 그것을 쓴다. 한국어 수사는 규칙이 닫혀 있어 결정적으로 풀린다
  - **못 읽으면 모델 값을 그대로 쓴다.** 대신하는 것이지 막는 것이 아니다 — "아까 그만큼" 같은 발화까지 파서가 감당할 수는 없다
  - `"원"` 에 붙은 수사만 읽는다. 발화에는 계좌번호가 함께 오고("삼오이이삼일오칠사구로 만원 보내줘"), 그 숫자를 금액으로 읽으면 엉뚱한 돈이 나간다. `SpokenAccountNumberParser` 가 자릿수를 품은 수사를 거부하는 것과 짝이다
  - 원문에서 읽은 금액은 **신뢰도를 1로 둔다.** 모델이 낮게 매긴 신뢰도를 그대로 쓰면 옳은 금액을 두고 다시 묻게 된다
  - 1억을 넘으면 읽지 않는다. 계좌번호가 배수에 붙으면(" 3522315749 만원") 조 단위가 되는데, 한도에서 걸리기 전에 확인 문구가 사용자에게 그 값을 읽어 준다
  - 모델 값과 어긋나면 로그로 남긴다. 쌓이는 표현이 곧 AI 프롬프트를 고칠 자리다
- **2026-09-04** — 되물을 때 무엇이 빠졌는지 짚어 준다. 슬롯이 `RECIPIENT`·`AMOUNT` 둘뿐이라, 은행을 못 들었을 때도 계좌번호를 못 들었을 때도 똑같이 "누구에게 보내시겠어요?"를 되물었다. 이미 계좌번호를 말한 사용자에게는 답이 없는 질문이라 같은 자리를 맴돌았다.
  - `TransferClarification.voiceMessage()` 를 응답까지 실어 보낸다. 그전에는 검증이 문장을 정해도 `voice_commands` 로그에만 남고, 응답은 슬롯에서 문장을 다시 만들어 버렸다
  - 상황별 고정 문구를 넣었다 — 은행만 빠짐 / 계좌번호만 빠짐 / 등록되지 않은 이름 / 비슷한 이름이 여럿
  - 이름을 못 찾는 것을 예외에서 되물음으로 바꿨다. `RECIPIENT_NOT_FOUND` 로 끊으면 "저장된 분이 없어요"에서 대화가 끝나, 계좌번호를 말하면 보낼 수 있다는 것을 사용자가 알 수 없다
  - **비슷한 이름이 여럿일 때 "저장된 분이 없어요"라고 하던 것을 고쳤다.** 등록은 돼 있는데 고르지 못한 것이라 사실과 달랐고, 사용자는 등록을 다시 하려 든다. 임의로 고르지 않는 원칙은 그대로 두고 계좌번호로 가리게 한다
  - 되물을 때 **절반만 들은 계좌 정보를 지키도록** 고쳤다. 은행만 듣고 지워 버리면 사용자가 계좌번호를 대답해도 은행이 없어 또 되묻는다. 다만 둘 다 못 들었을 때는 그대로 버린다 — 낡은 계좌번호를 들고 있으면 사용자가 이름으로 답해도 계좌 쪽이 우선해 엉뚱한 곳으로 나간다
  - `missingSlots` 의 값은 그대로 `RECIPIENT`·`AMOUNT` 다. 프런트가 이 둘만 허용하도록 응답을 검증하고 있어, 슬롯을 늘리면 되물음 응답 자체가 거부된다

- **2026-09-03** — 상대방 등록에서 계좌 중복도 막는다 (#122). 별칭만 유일하게 걸어 뒀더니 같은 계좌를 "엄마"·"어머니"로 각각 등록할 수 있었다. `transfer_count`가 이름별로 쪼개져 FDS의 "처음 보내는 상대" 판단이 흐려지는 문제였다.
  - `transfer_recipients.account_num_hash` 추가. `account_num`은 무작위 IV로 암호화돼 직접 비교할 수 없어 `users.phone_hash`와 같은 HMAC-SHA256 검색 해시 패턴을 그대로 가져왔다
  - 등록 시 `(user_id, account_num_hash)` 존재 여부를 별칭 검사 바로 다음에 확인. DB에도 `uk_recipient_user_account`로 걸어 둔다
  - `ErrorCode.RECIPIENT_ACCOUNT_DUPLICATED`(TRANSFER_4092) 추가

- **2026-09-03** — 상대방 등록 추가. 이름만 불러 송금하려면 이름과 계좌가 미리 묶여 있어야 하는데 그 묶음을 만들 방법이 없었다.
  - `POST /api/transfers/recipients` 신설, `TransferRecipientCommandService` 추가
  - 받는 값은 이름과 계좌번호뿐. 은행코드·예금주는 입력받지 않고 `RegisteredAccountFinder`가 찾은 계좌에서 채운다 — 사람이 옮겨 적으면 틀리고, 틀린 은행으로 저장되면 이름을 불렀을 때 엉뚱한 곳으로 간다
  - 계좌 실재 여부는 **등록 시점에** 확인한다. 송금 순간에 확인하면 이미 늦다. 사용자는 이름만 불렀는데 그때 "그런 계좌가 없다"고 하면 무엇이 잘못됐는지 알 수 없다
  - 계좌 찾기는 `MockDepositAccountResolver`와 같은 접두어 규칙을 쓴다. `account_num_masked`가 마스킹된 값이라 완전 일치가 불가능하다. **후보가 둘 이상이면 거절한다** — 애매하게 저장하면 엉뚱한 사람에게 돈이 간다
  - `ErrorCode` 4건 추가: `RECIPIENT_ACCOUNT_AMBIGUOUS`(4008) · `SELF_RECIPIENT_NOT_ALLOWED`(4009) · `RECIPIENT_ACCOUNT_NOT_FOUND`(4043) · `RECIPIENT_NICKNAME_DUPLICATED`(4091)

- **2026-09-03** — 등록하지 않은 상대에게도 계좌번호를 말해 보낼 수 있게 했다(기획 변경). 그전에는 `validateDirectAccountNumber`가 계좌번호처럼 보이는 수취인을 거부했다 — "오타 한 번이 모르는 계좌로 가는 이체가 된다"는 이유였다. 그 위험은 그대로이므로 확인 단계에서 은행과 뒤 네 자리를 복창하고, 자릿수를 품은 수사("삼천오백")는 계좌번호로 읽지 않는다.

- **2026-08-28** — 직접 입력 송금 검토·실행과 등록 수취인 목록 추가
  - `GET /api/transfers/recipients`, `POST /api/transfers/review`, `POST /api/transfers` 신설
  - `TransferConfirmationStore`로 검토 스냅샷을 서버가 소유. 실행 요청은 확인 ID와 멱등성 키만 받는다
  - 출금 계좌·수취인 소유권 조회를 `TransferTargetResolver`로 모아 음성 경로와 공유
  - `ErrorCode.CONFIRMATION_INVALID`(TRANSFER_4007) 추가
  - `movi.transfer.confirmation-expire-minutes`(기본 5분) 추가 — 화면 검토는 음성 확인 60초보다 길다
- **2026-08-28** — 직접 입력 실행 요청이 `deviceUuid`를 받아 FDS 신뢰 기기 피처로 넘긴다 (#96). 음성 세션과 달리 기기를 들고 있을 세션이 없어 실행 요청이 직접 실어 보낸다.
- **2026-08-28** — 직접 입력 송금 E2E 추가 (#107). 단위 테스트는 `TransferExecutionService`를 목으로 대체해 컨트롤러부터 FDS·오픈뱅킹까지 실제로 이어지는지, 멱등성이 DB 제약까지 포함해 동작하는지를 보지 못한다. 접근성 대안이지만 **똑같이 돈이 나가는 경로**라 음성과 같은 수준으로 고정했다.
- **2026-08-25** — 거래내역 단건 상세 조회와 음성 안내 추가 (#71)
  - `GET /api/transactions/{transactionId}` 신설, `TransactionDetailResponse` 추가
  - 목록·상세 응답에 `voiceMessage` 채움 (기존 목록 응답에는 없었다)
  - `ErrorCode.TRANSACTION_NOT_FOUND`(TRANSFER_4042) 추가
