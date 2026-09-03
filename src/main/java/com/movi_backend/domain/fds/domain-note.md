# fds 도메인

이상거래 탐지 평가와 결과 저장을 다룬다. 여기서 이체가 완료·차단으로 갈린다.

도메인 전반의 불변식은 [docs/domain-guide.md](../../../../../../../docs/domain-guide.md)가 기준이다. 이 문서는 실제 AI 서버 연동 과정에서 확인한 사실을 패키지 내부 관점에서 보충한다.

## 두 계약이 따로 있다

| | 내부 계약 | AI 서버 계약 |
|---|---|---|
| 타입 | `FdsAssessmentRequest`/`FdsAssessmentResponse` | `FraudDetectionRequest`/`FraudDetectionResponse` (`client/dto` 안에 함께 있지만 필드명이 snake_case) |
| 누가 쓰나 | `TransferExecutionService`, `MockFdsAssessmentClient`, 검증기, 저장 로직 | `HttpFdsAssessmentClient`만 |
| 요청 모양 | 위험 피처(수취인 신뢰도·30일 프로필·기기 신뢰) | 원시 거래(계좌·은행·금액·일시) + 과거 거래 목록 |
| 응답 모양 | `riskLevel`(LOW/MEDIUM/HIGH) + `decision` | `anomaly_score`·`rule_score`·`final_risk_score`·`risk_level`·`triggered_rules` |

**변환은 `HttpFdsAssessmentClient` 안에서만 한다.** `TransferExecutionService`도 검증기도 Mock도 AI가 실제로 어떤 필드를 쓰는지 몰라도 되게 하기 위해서다. `openapi.json`이 바뀌어도 고칠 곳은 이 어댑터 하나다.

## AI의 risk_level을 그대로 신뢰한다

`https://moviback.duckdns.org/ai/fds/openapi.json`에는 `risk_level`·`rule_score`·`final_risk_score`가 전부 `nullable`로 적혀 있고 설명에 "이후 필드를 확장한다"고 돼 있다. **실제로는 이미 채워져 온다** — 2026-09-02에 운영 AI 서버로 여러 거래를 직접 보내 확인했다.

```text
final_risk_score=12.0  → risk_level=LOW
final_risk_score=57~69 → risk_level=MEDIUM
final_risk_score=82.5  → risk_level=HIGH
```

`triggered_rules`에 `HIGH_AMOUNT_RATIO`·`NIGHT_TRANSACTION`·`NEW_RECIPIENT` 같은 규칙 이름이 같이 온다 — 이미 규칙 엔진을 거친 판정이다. 그래서 **백엔드가 점수 구간을 새로 정의하지 않는다.** AI가 준 문자열을 `RiskLevel.valueOf(...)`로 그대로 매핑하고, 비어 있거나 모르는 값이 오면 평가 실패로 처리해 이체를 진행하지 않는다(fail-closed). `docs/domain-guide.md`가 "AI가 반환한 위험도와 결정 조합만 검증한다"고 못박은 것과 같은 방향이다.

## 점수는 스케일이 다르다

AI의 `anomaly_score`(Isolation Forest 원본)는 0~1이지만, `rule_score`·`final_risk_score`는 **0~100**이다. 내부 `FdsScores`·검증기는 세 값 모두 0~1을 가정한다. 그래서 어댑터가 두 값만 100으로 나눠 맞춘다 — **값의 의미를 다시 해석하는 게 아니라 단위만 맞추는 것**이다. AI가 나중에 응답에 스케일을 명시하면 이 변환은 지운다.

## 원시 거래 정보는 `FdsAssessmentRequest`에 실어 보낸다

AI 서버는 계좌번호·은행 코드가 있는 원시 거래를 원하는데, 내부 요청은 위험 피처만 갖고 있었다. 어댑터가 `Transfer`를 다시 조회하는 대신, 호출부(`TransferExecutionService`)가 이미 들고 있는 값(`fromFintechUseNum`·`fromBankCode`·`toBankCode`·`toAccountNumEncrypted`)을 `FdsAssessmentRequest`에 함께 실어 보낸다. `MockFdsAssessmentClient`는 이 필드들을 쓰지 않는다 — 실제 AI 스키마를 만들 때만 필요하다. 수취인 계좌는 암호문 그대로 두고, 복호화는 어댑터가 실제로 보낼 때만 한다.

## `history`를 비우면 위험탐지가 꺼진다

**과거 대비 비율을 보는 AI 규칙은 이력이 없으면 아예 발동하지 않는다.** `HIGH_AMOUNT_RATIO`가 대표적이다. 이력을 빈 배열로 보내면 금액이 1만원이든 1000만원이든 전부 LOW 로 판정된다 — 위험탐지가 켜져 있는데도 아무것도 걸리지 않는 상태가 된다. 2026-09-02 운영 AI 서버로 확인했다.

```text
이력 없음 / 1만원 ~ 1000만원   → 전부 LOW 12.0
이력 없음 / 85만원·심야·음성   → LOW 21.04
이력 25건 / 85만원·심야·음성   → MEDIUM 45.39  (HIGH_AMOUNT_RATIO 발동)
```

그래서 `TransferExecutionService`가 같은 출금계좌의 **최근 30일 출금(최대 100건)** 을 읽어 `FdsAssessmentRequest.history`에 실어 보낸다. 30일은 `user_transfer_profiles`의 집계 구간과 맞춘 값이다.

### 이력의 필드는 셋으로 나뉜다

| | 값 | 근거 |
|---|---|---|
| `amount`·`transaction_datetime` | 실제 값 | 점수를 직접 바꾼다 |
| `receiver_account` | 실제 값(복호화) | 현재 수취인이 이력에 있으면 AI가 `NEW_RECIPIENT`를 뺀다. 현재 거래와 표기가 어긋나면 재이체인데도 매번 신규 수취인으로 잡힌다 |
| `receiver_bank` | 출금계좌의 은행 코드 | **AI가 쓰지 않는다.** 004·088·020을 각각 넣어도 점수가 소수점까지 같았다 |
| `medium` | 현재 거래와 같은 값 | 아래 참고 |

`receiver_bank`를 두고 예전에는 "은행 코드를 지어내면 z-score·패턴 피처가 왜곡되므로 빈 이력을 택했다"고 적었으나, 실제로 값을 바꿔가며 확인한 결과 **이력의 은행 코드는 점수에 아무 영향이 없었다.** 상대 은행 코드 컬럼을 추가하는 스키마 변경은 필요하지 않다.

### `medium`은 현재 거래와 같게 보낸다

거래별 유입 경로(음성/화면)를 저장하지 않아 과거 경로를 알 수 없다. 여기에 임의의 값을 넣으면 AI의 `UNUSUAL_MEDIUM`이 사실과 무관하게 발동한다. 특히 **음성이 기본 경로인 이 서비스에서 이력을 전부 APP으로 적으면 정상적인 음성 송금이 매번 경로 이상으로 잡힌다**(같은 거래가 60.68 대 45.39로 갈렸다). 없는 정보로 위험 신호를 만들지 않기 위해, 규칙이 발동하지 않는 쪽을 택했다. 경로를 `transactions`에 남기게 되면 그때 실제 값으로 바꾼다.

### 거래 식별자는 요청 안에서 겹치면 안 된다

AI 는 `current_transaction` 과 `history` 의 `transaction_id` 가 하나라도 겹치면 **요청 전체를 400 으로 거절한다**(`InvalidTransactionRequest`). 백엔드가 이 필드를 비워 두면 전부 `None` 으로 같아지므로, 이력을 함께 보내는 순간 모든 평가가 실패한다 — 2026-09-03 실제로 겪었다.

현재 거래는 `transfers`, 이력은 `transactions` 에서 온다. 서로 다른 테이블이라 숫자가 우연히 같을 수 있어 접두어로 가른다.

```text
현재 거래  transfer-{transferId}
이력      tx-{transactionId}
```

AI 는 이 값으로 여러 거래 중 어느 것이 지금 평가할 거래인지 찾는다. 비워 두면 "마지막 행" 으로 추측하는데, 그 추측이 맞는다는 보장이 없다.

### 이력 때문에 이체가 막히지는 않는다

이력은 정확도를 높이는 보조 정보다. 조회가 실패하면 이력 없이 평가하고, 복호화되지 않는 항목(오픈뱅킹에서 받아 저장한 거래처럼 우리 키로 암호화되지 않은 값)은 건너뛴다. 한 건이 깨졌다고 평가 자체를 실패시켜 송금을 막지 않는다 — AI 응답이 잘못됐을 때의 fail-closed와는 다루는 대상이 다르다.

## 변경 이력

- **2026-09-03** — 정책 버전을 AI 응답에서 받는다. 그전에는 어댑터가 코드에 박아 둔 `movi-fraud-detection-api-0.4.0` 을 기록했는데, AI 는 그 사이 0.6.0 이 됐다. 감사 기록이 두 버전 낡은 값을 달고 있었다. AI 가 `policy_version` 을 싣도록 하고, 없으면 특정 버전 대신 `...-unknown` 을 남겨 기록만 보고 실제 버전이라 오해하지 않게 했다.

- **2026-09-03** — 거래 식별자를 실어 보낸다. AI 가 `transaction_id` 중복 검사를 추가하면서, 식별자를 비워 두던 백엔드 요청이 이력을 담는 순간 전부 400 으로 거절됐다. 두 변경이 각각은 맞는데 함께 두니 깨진 경우다.

- **2026-09-02** — `history`를 실제 거래이력으로 채운다. 빈 배열로 보내면 과거 대비 비율 규칙이 발동하지 않아 금액과 무관하게 LOW 만 나오는 것을 운영 AI 서버로 확인했다. 이력의 은행 코드가 점수에 영향이 없다는 것도 함께 확인해, 스키마 변경 없이 채울 수 있게 됐다.

- **2026-09-02** — 실제 AI FDS 서버(`POST /api/v1/fraud/detect`, 기존 경로 `/internal/v1/fraud/predict`는 틀린 값이었다)와 연동했다. 요청·응답 스키마가 내부 계약과 완전히 달라 `HttpFdsAssessmentClient`를 새 계약대로 다시 작성하고, `FraudDetectionRequest`/`TransactionData`/`FraudDetectionResponse` wire DTO를 추가했다. `FdsAssessmentRequest`에 원시 계좌 식별자 4개를 추가했다.
