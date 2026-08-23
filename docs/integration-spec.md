# 프론트·AI·백엔드 통합 명세

버전: `v1.0`

기준일: `2026-08-14`

적용 범위: 2026년 8월 Movi MVP

이 문서는 음성 입력부터 이체 결과 안내까지의 파트별 책임과 통합 정책을 확정한다. 구현 중 다른 문서와 충돌하면 데이터 모델은 [ERD.md](ERD.md), API 응답 외피는 [api-response.md](api-response.md), 파트 간 통합 결정은 이 문서를 우선한다.

---

## 1. 목표와 비목표

### 1.1 목표

아래 사용자 흐름을 화면 조작 없이 완주한다.

```text
로그인
→ 음성 녹음
→ STT 및 Intent/Entity 추출
→ 백엔드 필수값·권한 검증
→ 누락값 재질문 또는 이체 내용 확인
→ 사용자 최종 확인
→ FDS 평가
→ 이체 실행 또는 차단
→ 보호자 알림
→ 음성 결과 안내
```

### 1.2 MVP 비목표

- 음성으로 새 계좌번호를 직접 불러 송금하기
- 보호자의 사전 승인 대기
- Redis 기반 대화 세션
- 카드 거래 FDS 모델
- SHAP 시각화
- 계좌 추가 연결과 연결 해제
- 음성 속도 등 TTS 고급 설정
- 다중 서버·무중단 배포·Kubernetes

---

## 2. 확정 아키텍처

### 2.1 호출 경로

```text
[Frontend]
  │ 1. multipart 음성 업로드
  ▼
[Spring Backend]
  │ 2. 인증·세션·파일 검증
  │ 3. 내부 Voice API 호출
  ▼
[AI Voice API]
  │ Google STT → 정규화 → Intent/Entity
  ▼
[Spring Backend]
  │ 4. 슬롯·계좌·수취인 검증
  │ 5. 확인 완료 후 이체 생성
  │ 6. 내부 FDS API 호출
  ▼
[AI FDS API]
  │ 파생 피처 → 모델·룰 → 위험도
  ▼
[Spring Backend]
  │ 7. 오픈뱅킹 이체/차단·알림·상태 저장
  ▼
[Frontend]
  8. voiceMessage 표시 및 기기 TTS 재생
```

프론트는 AI API를 직접 호출하지 않는다. AI와 오픈뱅킹 인증정보는 백엔드 또는 AI 서버에만 둔다.

### 2.2 TTS

MVP의 필수 경로는 백엔드가 반환하는 `voiceMessage`를 프론트의 기기 TTS로 읽는 방식이다. Google TTS 연동은 선택 기능이며, 실패해도 잔액조회·이체 결과를 실패 처리하지 않는다. 모든 금융 결과는 TTS와 무관하게 텍스트 `voiceMessage`를 포함한다.

---

## 3. 12개 통합 결정

| 번호 | 결정 항목 | 확정 내용 |
|---:|---|---|
| 1 | STT 호출자 | AI Voice API가 Google STT를 호출한다. |
| 2 | 음성 전달 경로 | `프론트 → 백엔드 → AI`로 고정한다. |
| 3 | 세션 ID | 백엔드가 DB `voice_sessions.session_id`를 생성한다. |
| 4 | Intent | `BALANCE`, `TRANSFER`, `HISTORY`, `CONFIRM`, `CANCEL`, `UNKNOWN`을 MVP로 사용한다. 기존 `GUARDIAN`, `SETTING`은 예약값이다. |
| 5 | Entity | 고정 필드를 가진 JSON 객체를 사용하고 실제 계좌·수취인 조회는 백엔드가 한다. |
| 6 | 누락 필드 | 키를 생략하지 않고 값에 `null`을 사용한다. |
| 7 | 재질문 세션 | 백엔드가 슬롯 저장·병합·만료의 단일 소유자다. |
| 8 | 확인 문장 | 백엔드가 검증된 정보로 만들고 프론트가 TTS로 읽는다. |
| 9 | FDS 피처 | 백엔드가 사실·집계 데이터를 제공하고 AI가 모델 종속 파생 피처를 계산한다. |
| 10 | Cold start | `coldStart=true`를 전달하고 AI가 별도 룰 정책을 적용한다. 구체 정책은 8.3절을 따른다. |
| 11 | FDS 임계값 | Mock은 고정 개발 임계값, 실모델은 검증 데이터 승인 임계값과 `policyVersion`을 사용한다. |
| 12 | 타임아웃·Mock | Voice 10초, FDS 3초, 자동 재시도 없음. Mock 제공 규격은 [ai-api-contract.md](ai-api-contract.md)를 따른다. |

---

## 4. 파트별 단일 책임

| 기능 | 프론트 | 백엔드 | AI |
|---|---|---|---|
| 마이크 권한·녹음 | 주 담당 | 파일 검증 | 포맷 디코딩 |
| STT | 결과 표시 | 호출·오류 변환 | 주 담당 |
| 텍스트 정규화 | 없음 | 금융 유효성 재검증 | 주 담당 |
| Intent/Entity | 전달하지 않음 | 스키마·필수값 재검증 | 주 담당 |
| 음성 세션 | ID 보관 | 생성·소유권·종료 | 받은 ID 반향 |
| 슬롯 | 현재 UI 상태만 보관 | 저장·병합·만료 | 현재 발화만 추출 |
| 실제 계좌·수취인 | 표시 | DB 조회·소유권 검증 | 조회 금지 |
| 확인 문장 | 표시·TTS | 생성·확인 상태 관리 | 후속 발화 분류 |
| 멱등성 | 키 생성·재사용 | 검증·DB UNIQUE 방어 | 없음 |
| FDS 사실 데이터 | 없음 | 수집·전달 | 수신 |
| 파생 Feature·추론 | 없음 | 결과 검증 | 주 담당 |
| 실제 이체 | 결과 표시 | 주 담당 | 실행 금지 |
| 보호자 알림 | 상태 표시 | 주 담당 | 없음 |
| 금융 상태 | UI 표현 | 단일 소유자 | 변경 금지 |

AI는 사용자 DB와 오픈뱅킹을 직접 조회하지 않는다. 프론트는 AI가 추출한 값만으로 금융 결과를 확정하지 않는다.

---

## 5. 공개 API 흐름

모든 JSON 응답은 [api-response.md](api-response.md)의 `ApiResponse`로 감싼다.

### 5.1 음성 세션 시작

```http
POST /api/voice/sessions
Authorization: Bearer {accessToken}
```

```json
{
  "code": "SUCCESS",
  "message": "요청이 정상 처리되었습니다.",
  "voiceMessage": "무엇을 도와드릴까요?",
  "data": {
    "voiceSessionId": 15,
    "status": "ACTIVE",
    "expiresAt": "2026-08-14T10:05:00+09:00"
  }
}
```

### 5.2 음성 명령 전송

```http
POST /api/voice/sessions/{voiceSessionId}/commands
Authorization: Bearer {accessToken}
Content-Type: multipart/form-data
```

파트:

| 이름 | 형식 | 필수 | 제약 |
|---|---|---:|---|
| `audio` | file | 예 | WebM/Opus 또는 WAV, 최대 5MB·15초 |
| `idempotencyKey` | UUID string | 확인 발화만 | 확인 화면에서 생성한 동일 키 재사용 |

백엔드는 현재 사용자의 세션인지 확인한 뒤 AI Voice API를 호출한다.

### 5.3 재질문 응답

```json
{
  "code": "SUCCESS",
  "message": "추가 정보가 필요합니다.",
  "voiceMessage": "얼마를 보내시겠어요?",
  "data": {
    "voiceSessionId": 15,
    "state": "CLARIFYING",
    "intent": "TRANSFER",
    "missingSlots": ["AMOUNT"],
    "expiresAt": "2026-08-14T10:01:00+09:00"
  }
}
```

슬롯 누락은 대화의 정상 분기이므로 공개 Voice API에서는 `200`으로 반환한다. 기존 `AMOUNT_MISSING`, `RECIPIENT_MISSING` 에러는 별도의 직접 실행 API나 내부 검증 실패에 사용한다.

### 5.4 확인 대기 응답

```json
{
  "code": "SUCCESS",
  "message": "이체 정보를 확인해 주세요.",
  "voiceMessage": "생활비 통장에서 김영희 님에게 오만 원을 보낼까요?",
  "data": {
    "voiceSessionId": 15,
    "state": "AWAITING_CONFIRMATION",
    "confirmationId": "c14c5b4d-a394-4d67-8788-bc716e5a60b6",
    "fromAccount": {
      "accountId": 12,
      "alias": "생활비 통장",
      "bankName": "국민은행"
    },
    "recipient": {
      "holderName": "김영희",
      "bankName": "신한은행",
      "maskedAccountNumber": "110-***-123456"
    },
    "amount": 50000,
    "expiresAt": "2026-08-14T10:01:00+09:00"
  }
}
```

프론트는 이 응답을 받은 시점에 UUID `idempotencyKey` 하나를 만들고 확인 재시도 동안 유지한다.

### 5.5 확인 완료 응답

```json
{
  "code": "SUCCESS",
  "message": "이체가 완료되었습니다.",
  "voiceMessage": "김영희 님에게 오만 원을 보냈어요.",
  "data": {
    "transferId": 101,
    "status": "COMPLETED",
    "riskLevel": "LOW",
    "amount": 50000,
    "completedAt": "2026-08-14T10:00:30+09:00"
  }
}
```

HIGH는 `403`과 `FDS_4031`, FDS 통신 실패는 `502/504`를 사용한다. 두 경우 모두 실제 오픈뱅킹 이체를 호출하지 않는다.

### 5.6 이체 상태 조회

응답을 받지 못한 네트워크 타임아웃에서는 새 키를 만들지 않고 확인 요청에 사용한 키로 상태를 조회한다.

```http
GET /api/transfers/status?idempotencyKey={UUID}
Authorization: Bearer {accessToken}
```

백엔드는 인증 사용자와 키가 모두 일치하는 이체만 반환한다. `PENDING`, `RISK_REVIEW`,
`COMPLETED`, `BLOCKED`, `FAILED`, `CANCELED`를 상태 데이터로 반환하며 계좌번호는 포함하지 않는다.

---

## 6. 음성 세션·슬롯 정책

### 6.1 상태

```text
ACTIVE
├─ CLARIFYING
├─ AWAITING_CONFIRMATION
│  ├─ PROCESSING → COMPLETED
│  └─ CANCELED
└─ EXPIRED
```

### 6.2 만료와 재시도

| 항목 | 값 |
|---|---:|
| 일반 세션 유효시간 | 마지막 활동 후 5분 |
| 누락 슬롯 유효시간 | 마지막 재질문 후 60초 |
| 확인 대기 유효시간 | 확인 문장 생성 후 60초 |
| 같은 슬롯 재질문 | 최대 3회 |

3회 초과 시 세션을 종료하고 `VOICE_4006`을 반환한다. 직접 입력 UI는 MVP 필수가 아니므로 현재 `RETRY_LIMIT_EXCEEDED` 음성 문구의 “직접 입력으로 바꿔 드릴게요”는 구현 시 “잠시 후 다시 시도해 주세요”로 수정한다.

### 6.3 TRANSFER 필수 슬롯

| 슬롯 | 필수 | 누락 시 처리 |
|---|---:|---|
| `amount` | 예 | “얼마를 보내시겠어요?” |
| `recipient` | 예 | “누구에게 보내시겠어요?” |
| `sourceAccountAlias` | 아니요 | 현재 사용자의 기본 계좌 사용 |

필수 슬롯 두 개가 모두 없으면 수취인을 먼저 질문한다. 오래된 슬롯은 전부 폐기하며 일부만 살리지 않는다.

### 6.4 확인 정보 불변성

`AWAITING_CONFIRMATION` 이후 금액·출금계좌·수취인이 바뀌면 기존 `confirmationId`와 `idempotencyKey`를 폐기하고 확인 문장을 새로 생성한다.

---

## 7. AI 신뢰도 정책

confidence 범위는 `0.0000~1.0000`으로 고정한다.

| 구간 | 처리 |
|---|---|
| `0.80 이상` | 다음 백엔드 검증으로 진행 |
| `0.60 이상 0.80 미만` | 자동 실행하지 않고 전체 발화 재요청 |
| `0.60 미만` 또는 null | `VOICE_4004`로 재발화 요청 |

`sttConfidence`와 `nluConfidence` 중 하나라도 기준 미만이면 더 낮은 구간을 따른다. 금액·수취인의 개별 confidence가 `0.80` 미만이면 해당 슬롯을 누락으로 처리한다. confidence가 높아도 실제 DB·한도 검증을 생략하지 않는다.

---

## 8. 금융·FDS 정책

### 8.1 이체 한도

MVP 음성 이체 정책값:

| 항목 | 값 |
|---|---:|
| 최소 금액 | 1원 |
| 1회 한도 | 1,000,000원 |
| 1일 누적 한도 | 3,000,000원 |

하드코딩하지 않고 설정값으로 관리한다. 오픈뱅킹 한도가 더 낮으면 더 낮은 값을 적용한다.

### 8.2 FDS 분기

```text
LOW    + ALLOW             → 이체 완료, 보호자 알림 없음
MEDIUM + ALLOW_WITH_ALERT  → 이체 완료, 보호자 사후 알림
HIGH   + BLOCK             → 이체 미실행, 보호자 긴급 알림
```

다른 조합, 타임아웃, 통신 오류, 역직렬화 실패, 필수값 누락은 평가 실패다. 평가 실패 시 이체하지 않는다.

### 8.3 Cold start

최근 30일 성공 이체가 3건 미만이면 `coldStart=true`다. 실모델이 학습되기 전 Mock 및 룰 정책은 다음과 같다.

| 조건 | 최소 위험도 |
|---|---|
| 신뢰 기기 + 등록 수취인 + 100,000원 이하 | LOW 가능 |
| 신규 수취인 | MEDIUM |
| 비신뢰 기기 | MEDIUM |
| 신규 수취인 + 500,000원 이상 | HIGH |
| 비신뢰 기기 + 500,000원 이상 | HIGH |

모델 결과가 위 최소 위험도보다 낮으면 룰이 상향한다. 정책 변경 시 `policyVersion`을 변경한다.

### 8.4 임계값

Mock `risk-policy-dev-v1`의 개발 임계값:

```text
0.00 <= finalRiskScore < 0.40 → LOW
0.40 <= finalRiskScore < 0.75 → MEDIUM
0.75 <= finalRiskScore <= 1.00 → HIGH
```

실모델에는 이 수치를 자동 적용하지 않는다. AI 파트가 validation/test의 Recall, Precision, F1, PR-AUC, 오탐률과 임계값 후보를 제출하고 팀이 승인한 값을 `risk-policy-v1`로 고정한다. 백엔드는 임계값을 재계산하지 않고 AI가 반환한 위험도와 결정 조합만 검증한다.

---

## 9. 프론트 상태와 멱등성

### 9.1 화면 상태

```text
IDLE
LISTENING
UPLOADING
ANALYZING
CLARIFYING
AWAITING_CONFIRMATION
CHECKING_RISK
TRANSFERRING
COMPLETED
BLOCKED
ERROR
```

`CHECKING_RISK` 또는 `TRANSFERRING`에서는 확인 음성을 다시 전송하지 못하게 한다. 결과가 불명확한 네트워크 타임아웃에는 새 키를 만들지 않고 같은 `idempotencyKey`로 상태를 조회하거나 재요청한다.

### 9.2 프론트 보관값

- `voiceSessionId`
- `confirmationId`
- `idempotencyKey`
- 현재 UI 상태

계좌번호 원문, 오픈뱅킹 토큰, AI API 키는 저장하지 않는다.

---

## 10. 보안·개인정보

- 운영에서 `movi.auth.dev-mode=false`를 강제한다.
- JWT 인가가 완성되기 전 서버를 인터넷에 공개하지 않는다.
- 음성 파일은 AI 처리 완료 후 즉시 삭제하고 장기 저장하지 않는다.
- STT 원문은 `VoiceCommand` 추적에 필요한 기간만 보관하며 계좌번호가 포함되면 마스킹한다.
- MySQL `3306`은 외부에 공개하지 않는다.
- 계좌번호·전화번호·토큰을 로그에 남기지 않는다.
- AI 요청에는 계좌번호 원문, 전화번호, 토큰을 포함하지 않는다.
- AI는 `userId`, `transferId`를 추적 키로만 사용하고 별도 개인정보와 결합하지 않는다.

---

## 11. 현재 코드와의 필수 정합 작업

이 문서를 구현하려면 다음 변경이 필요하다.

1. `VoiceIntent`에 `CONFIRM`, `CANCEL` 추가
2. `voice_sessions`에 상태, `pending_intent`, `pending_slots`, `expires_at`, `retry_count` 저장 구조 추가
3. 엔티티 변경 시 `schema.sql`, `ERD.md`, ERDCloud SQL 동시 수정
4. `RECIPIENT_NOT_FOUND` 음성 문구에서 직접 계좌번호 발화를 유도하는 표현 제거
5. `RETRY_LIMIT_EXCEEDED` 음성 문구에서 미구현 직접 입력 안내 제거
6. FDS `policyVersion`, `ruleScore`, `finalRiskScore`, `reasonCodes`는 MVP에서 `features` JSON 스냅샷에 함께 저장
7. 이후 운영 분석 요구가 생기면 전용 컬럼 추가를 별도 스키마 변경으로 수행

---

## 12. 변경 통제

- 이 계약의 enum·필드·정책을 바꾸면 프론트·AI·백엔드 담당자가 모두 확인한다.
- 호환되지 않는 AI 계약 변경은 `/internal/v2`처럼 API 버전을 올린다.
- 변경 PR에는 정상/오류 JSON과 영향받는 파트를 적는다.
- 구두 합의만으로 계약을 바꾸지 않고 이 문서를 먼저 갱신한다.
