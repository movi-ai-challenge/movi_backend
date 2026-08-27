# AI Voice·FDS 내부 API 계약

버전: `v1.0`

기준일: `2026-08-14`

호출자: Spring Backend

제공자: AI 파트

이 문서는 Spring Backend와 AI 서버 사이의 내부 API를 고정한다. 외부 프론트 API는 [integration-spec.md](integration-spec.md), 공통 응답은 [api-response.md](api-response.md)를 따른다.

---

## 1. 공통 규칙

- 내부 API Base URL은 환경변수로 주입한다.
- 날짜·시간은 ISO-8601, 시간대는 `Asia/Seoul` 오프셋을 포함한다.
- 금액은 KRW 원 단위 정수다.
- confidence와 score는 `0.0~1.0`이다.
- JSON 필드는 `camelCase`다.
- 필드가 없으면 키를 유지하고 값에 `null`을 사용한다.
- 모든 요청에 백엔드 생성 `requestId`를 포함하고 응답은 같은 값을 반환한다.
- 요청·응답에 계좌번호, 전화번호, 인증 토큰을 넣지 않는다.
- AI 서버 로그에도 민감정보를 남기지 않는다.

내부 오류 외피:

```json
{
  "requestId": "voice-123",
  "error": {
    "code": "STT_PROVIDER_ERROR",
    "message": "STT provider request failed",
    "retryable": true
  }
}
```

내부 오류의 `message`는 사용자에게 직접 전달하지 않는다. 백엔드가 `ErrorCode.voiceMessage`로 변환한다.

---

## 2. Voice Analysis API

### 2.1 Endpoint

```http
POST /internal/v1/voice/analyze
Content-Type: multipart/form-data
```

| 파트 | 타입 | 필수 | 설명 |
|---|---|---:|---|
| `audio` | file | 예 | WebM/Opus, WAV 또는 Safari/iOS MP4·M4A, 최대 5MB·15초 |
| `requestId` | string | 예 | 백엔드 생성 UUID |
| `voiceSessionId` | long | 예 | 백엔드 세션 ID |
| `expectedIntent` | string | 아니요 | 재질문 중이면 `TRANSFER` 등, AI 분류의 강제값은 아님 |
| `expectedSlots` | JSON string | 아니요 | `['AMOUNT']` 등 현재 질문 맥락 |

AI는 이전 슬롯 값 자체를 저장하거나 결합하지 않는다. `expectedSlots`는 “오만 원” 같은 짧은 후속 발화의 해석 문맥으로만 사용한다.

### 2.2 Intent

```text
BALANCE
TRANSFER
HISTORY
CONFIRM
CANCEL
UNKNOWN
```

`GUARDIAN`, `SETTING`은 백엔드 기존 예약값이며 MVP Voice API는 반환하지 않는다.

### 2.3 정상 응답

```json
{
  "requestId": "voice-123",
  "voiceSessionId": 15,
  "transcript": "엄마한테 오만 원 보내줘",
  "sttConfidence": 0.93,
  "intent": "TRANSFER",
  "intentConfidence": 0.96,
  "entities": {
    "amount": 50000,
    "recipient": "엄마",
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "entityConfidences": {
    "amount": 0.98,
    "recipient": 0.95,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "detectedMissingEntities": [],
  "processingMs": 731
}
```

### 2.4 금액 누락

```json
{
  "requestId": "voice-124",
  "voiceSessionId": 15,
  "transcript": "엄마한테 보내줘",
  "sttConfidence": 0.94,
  "intent": "TRANSFER",
  "intentConfidence": 0.96,
  "entities": {
    "amount": null,
    "recipient": "엄마",
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "entityConfidences": {
    "amount": null,
    "recipient": 0.95,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "detectedMissingEntities": ["AMOUNT"],
  "processingMs": 690
}
```

백엔드는 `detectedMissingEntities`를 참고하되 자체 필수 슬롯 검증 결과를 최종 사용한다.

### 2.5 후속 금액 발화

요청의 `expectedIntent=TRANSFER`, `expectedSlots=['AMOUNT']` 문맥에서:

```json
{
  "requestId": "voice-125",
  "voiceSessionId": 15,
  "transcript": "오만 원",
  "sttConfidence": 0.95,
  "intent": "TRANSFER",
  "intentConfidence": 0.88,
  "entities": {
    "amount": 50000,
    "recipient": null,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "entityConfidences": {
    "amount": 0.98,
    "recipient": null,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "detectedMissingEntities": ["RECIPIENT"],
  "processingMs": 521
}
```

AI는 이전 수취인을 응답에 다시 채우지 않는다. 백엔드가 저장된 `recipient=엄마`와 새 `amount=50000`을 병합한다.

### 2.6 확인·취소

```json
{
  "requestId": "voice-126",
  "voiceSessionId": 15,
  "transcript": "응 보내줘",
  "sttConfidence": 0.97,
  "intent": "CONFIRM",
  "intentConfidence": 0.98,
  "entities": {
    "amount": null,
    "recipient": null,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "entityConfidences": {
    "amount": null,
    "recipient": null,
    "sourceAccountAlias": null,
    "bankName": null,
    "startDate": null,
    "endDate": null
  },
  "detectedMissingEntities": [],
  "processingMs": 402
}
```

`CONFIRM`은 백엔드 세션이 `AWAITING_CONFIRMATION`일 때만 효력이 있다. 다른 상태에서 들어오면 백엔드가 거부한다.

### 2.7 Voice 오류

| HTTP | 내부 코드 | 백엔드 변환 | 재시도 |
|---:|---|---|---:|
| 400 | `UNSUPPORTED_AUDIO_FORMAT` | `REQ_4150` | 아니요 |
| 400 | `AUDIO_TOO_LONG` | `REQ_4000` | 새 녹음 |
| 422 | `EMPTY_TRANSCRIPT` | `VOICE_5000` | 예 |
| 502 | `STT_PROVIDER_ERROR` | `VOICE_5000` | 사용자 재발화 |
| 504 | `VOICE_ANALYSIS_TIMEOUT` | `VOICE_5000` | 사용자 재발화 |
| 500 | `MODEL_INFERENCE_ERROR` | `VOICE_5000` | 사용자 재발화 |

Backend timeout:

```text
connect: 1초
response: 10초
자동 재시도: 0회
```

---

## 3. FDS Assessment API

### 3.1 Endpoint

```http
POST /internal/v1/fraud/predict
Content-Type: application/json
```

### 3.2 요청

```json
{
  "requestId": "fds-transfer-101",
  "transferId": 101,
  "userId": 3,
  "amount": 50000,
  "balanceBefore": 320000,
  "requestedAt": "2026-08-14T14:30:00+09:00",
  "recipient": {
    "transferCount": 0,
    "firstTime": true
  },
  "profile": {
    "coldStart": false,
    "averageAmount30d": 42000,
    "maximumAmount30d": 100000,
    "stddevAmount30d": 11000.0,
    "transferCount30d": 8,
    "distinctRecipients30d": 3,
    "commonHours": [9, 12, 18]
  },
  "context": {
    "trustedDevice": true,
    "sttConfidence": 0.93
  }
}
```

필수 필드:

| 필드 | 필수 | 비고 |
|---|---:|---|
| `requestId` | 예 | 백엔드 생성, 응답 일치 검증 |
| `transferId` | 예 | 이체 1건당 평가 1건 |
| `userId` | 예 | 모델 추적키, 개인정보 결합 금지 |
| `amount` | 예 | 1 이상 |
| `balanceBefore` | 예 | 음수 불가 |
| `requestedAt` | 예 | 시간 파생 피처 원천 |
| `recipient.transferCount` | 예 | 0 이상 |
| `recipient.firstTime` | 예 | transferCount==0과 일치 |
| `profile.coldStart` | 예 | 아래 null 규칙 결정 |
| `context.trustedDevice` | 예 | null 금지 |
| `context.sttConfidence` | 예 | 0~1 |

`coldStart=true`이면 평균·최대·표준편차는 `null`, 횟수는 `0`, `commonHours`는 빈 배열을 사용한다.

### 3.3 피처 계산 책임

| 파생 피처 | AI 계산식/규칙 |
|---|---|
| `amountVsMean` | `amount / averageAmount30d`, cold start면 null |
| `amountZscore` | `(amount-average)/stddev`, stddev 0 또는 null이면 null |
| `amountToBalanceRatio` | balance가 0이면 1.0, 아니면 amount/balance |
| `isNight` | Asia/Seoul 00:00~05:59 |
| `isWeekend` | 토·일 |
| `recipientSeenBefore` | `transferCount > 0` |
| `userHourFrequency` | commonHours와 현재 hour 기반, 모델 정의서에 계산식 기록 |

학습 파이프라인과 추론 API는 동일한 전처리 코드와 저장된 scaler/encoder를 사용한다.

### 3.4 정상 응답

```json
{
  "requestId": "fds-transfer-101",
  "modelVersion": "isolation-forest-v1",
  "policyVersion": "risk-policy-v1",
  "scores": {
    "anomalyScore": 0.72,
    "ruleScore": 0.85,
    "finalRiskScore": 0.79
  },
  "riskLevel": "HIGH",
  "decision": "BLOCK",
  "reasonCodes": [
    "HIGH_AMOUNT",
    "NEW_RECIPIENT"
  ],
  "latencyMs": 57
}
```

허용 reason code:

```text
HIGH_AMOUNT
AMOUNT_DEVIATION
NEW_RECIPIENT
UNUSUAL_TIME
NEW_DEVICE
LOW_STT_CONFIDENCE
COLD_START
REPEATED_TRANSFER
```

새 코드를 추가해도 백엔드는 무시할 수 있어야 하며, 기존 코드 의미를 바꾸지 않는다.

### 3.5 응답 검증

백엔드는 다음을 모두 검사한다.

- 응답 `requestId`가 요청과 동일
- `modelVersion`, `policyVersion`이 비어 있지 않음
- 세 score가 `0~1`
- risk/decision이 아래 조합 중 하나
- `latencyMs`가 0 이상

```text
LOW + ALLOW
MEDIUM + ALLOW_WITH_ALERT
HIGH + BLOCK
```

검증 실패는 `ASSESSMENT_FAILED`이며 실제 이체를 호출하지 않는다.

### 3.6 FDS 오류

| HTTP | 내부 코드 | 재시도 | 백엔드 처리 |
|---:|---|---:|---|
| 400 | `INVALID_FEATURE` | 아니요 | 평가 실패·미이체 |
| 404 | `MODEL_NOT_FOUND` | 아니요 | 평가 실패·미이체 |
| 409 | `ASSESSMENT_CONFLICT` | 상태 조회 | 기존 평가 확인 |
| 422 | `FEATURE_MISSING` | 아니요 | 평가 실패·미이체 |
| 500 | `INFERENCE_ERROR` | 아니요 | 평가 실패·미이체 |
| 503 | `MODEL_UNAVAILABLE` | 아니요 | 평가 실패·미이체 |
| 504 | `INFERENCE_TIMEOUT` | 아니요 | 평가 시간초과·미이체 |

Backend timeout:

```text
connect: 1초
response: 3초
자동 재시도: 0회
```

---

## 4. Mock 계약

AI 파트가 FastAPI Mock을 제공한다. Mock은 개발·테스트 프로파일에서만 사용할 수 있고 운영에서는 시나리오 강제 헤더를 거부한다.

### 4.1 시나리오 선택

```http
X-Mock-Scenario: VOICE_TRANSFER_COMPLETE
X-Mock-Scenario: FDS_HIGH
```

### 4.2 Voice 시나리오

```text
VOICE_TRANSFER_COMPLETE
VOICE_AMOUNT_MISSING
VOICE_RECIPIENT_MISSING
VOICE_BALANCE
VOICE_CONFIRM
VOICE_CANCEL
VOICE_UNKNOWN
VOICE_LOW_CONFIDENCE
VOICE_STT_ERROR
VOICE_TIMEOUT
```

### 4.3 FDS 시나리오

```text
FDS_LOW
FDS_MEDIUM
FDS_HIGH
FDS_COLD_START_LOW
FDS_COLD_START_HIGH
FDS_TIMEOUT
FDS_HTTP_500
FDS_INVALID_JSON
FDS_MISSING_FIELD
FDS_DECISION_MISMATCH
```

### 4.4 제공일

| 산출물 | 기한 |
|---|---|
| Voice OpenAPI/JSON 예제 | 8/14 |
| 실행 가능한 Voice Mock | 8/15 |
| FDS OpenAPI/JSON 예제 | 8/14 |
| 실행 가능한 FDS Mock | 8/17 |
| 실제 Voice staging API | 8/19 |
| 실제 FDS staging API | 8/23 |

제공이 지연돼도 백엔드는 같은 계약의 자체 Stub으로 개발을 계속한다.

---

## 5. 모델 승인 기준

AI 파트는 실 FDS 연결 전에 다음을 공유한다.

- 학습 데이터 기간·건수·정상/이상 비율
- train/validation/test 분리 방식과 데이터 누수 방지 설명
- 최종 피처 목록과 운영 피처 매핑표
- scaler/encoder/model 버전
- 임계값별 Precision, Recall, F1, PR-AUC, 오탐률
- 선택한 임계값의 근거
- cold-start 평가 결과
- 알려진 한계

금융 MVP에서는 이상거래 Recall을 우선하되, HIGH 오탐으로 정상 송금이 과도하게 차단되지 않도록 HIGH 오탐률을 별도 제시한다. 수치 목표는 데이터셋 분석 후 팀 리뷰에서 `policyVersion`과 함께 승인한다.
