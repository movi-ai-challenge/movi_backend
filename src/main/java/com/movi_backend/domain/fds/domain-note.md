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

**`history`는 항상 빈 배열로 보낸다.** `transactions` 테이블에 상대 은행 코드가 없어(계좌번호만 있다) 과거 거래를 AI 스키마로 정확히 재구성할 수 없다. 은행 코드를 지어내면 AI의 z-score·패턴 피처가 실제와 다른 값으로 왜곡되므로, 잘못된 값을 보내느니 빈 이력을 택했다. 채우려면 `transactions`에 상대 은행 코드 컬럼을 추가하는 스키마 변경이 먼저 필요하다.

## 변경 이력

- **2026-09-02** — 실제 AI FDS 서버(`POST /api/v1/fraud/detect`, 기존 경로 `/internal/v1/fraud/predict`는 틀린 값이었다)와 연동했다. 요청·응답 스키마가 내부 계약과 완전히 달라 `HttpFdsAssessmentClient`를 새 계약대로 다시 작성하고, `FraudDetectionRequest`/`TransactionData`/`FraudDetectionResponse` wire DTO를 추가했다. `FdsAssessmentRequest`에 원시 계좌 식별자 4개를 추가했다.
