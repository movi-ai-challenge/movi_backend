# AI·Frontend·Backend 통합 회의록 및 조치 목록

기준일: `2026-08-25`

추적 이슈: [#66](https://github.com/movi-ai-challenge/movi_backend/issues/66)

비교 기준:

- AI팀 공유 문서: `MOVI - 이상거래 탐지 Baseline 개발 기록`
- 파트 간 확정 계약: [integration-spec.md](integration-spec.md)
- Backend ↔ AI 내부 API: [ai-api-contract.md](ai-api-contract.md)
- Backend 기준 브랜치: `develop`

이 문서는 AI 구현의 완성도를 평가하기 위한 문서가 아니다. 각 파트가 독립적으로 구현한 결과를
실제 서비스 경로에 연결하기 전에, 서로 다른 이름·책임·전송 형식을 하나의 계약으로 맞추기 위한
공유 체크리스트다. 계약이 바뀌면 구현과 이 문서를 같은 PR에서 갱신한다.

## 0. 회의 요약

### 확정한 방향

1. **AI는 언어 문맥, Backend는 금융 상태를 소유한다.** AI는 대화의 Intent·Entity와 후속 발화
   문맥을 관리한다. Backend는 사용자·계좌 소유권, 검증된 슬롯, 확인 상태, 만료, 재인증 증명,
   멱등성 및 실제 실행을 관리한다. AI가 반환한 값만으로 송금하지 않는다.
2. **수취인은 별칭과 계좌번호를 모두 지원한다.** 등록된 별칭·계좌번호는 같은 수취인 조회 흐름으로
   연결하고, 미등록 계좌번호는 예금주 확인을 거친 일회성 수취인으로 처리한다.
3. **외부 Intent 이름과 지원 범위는 AI Schema를 따른다.** Backend 경계 Adapter가 이를 내부
   명령으로 변환한다.
4. **음성 전달은 AI Streaming STT를 사용한다.** Frontend가 AI를 직접 호출하지 않고 인증된
   Backend WebSocket을 거쳐 전달한다. 금융 실행에는 `final` 결과만 사용한다.
5. **FDS 점수·등급·실행 결정은 AI 정책을 따른다.** Backend는 응답 계약과 조합을 검증하고,
   오류·timeout·알 수 없는 결정에서는 송금을 실행하지 않는다.
6. **음성 일회용 숫자 따라 읽기는 송금 의사 확인이다.** 이는 사용자가 방금 안내받은 거래를
   명시적으로 승인했는지 확인하고 단순 재생·중복 요청을 줄이기 위한 절차이지, PIN·생체인증과 같은
   독립적인 본인 재인증 수단으로 간주하지 않는다.
7. **거래 내용이 바뀌면 기존 승인을 무효화한다.** 금액·수취인·출금 계좌 중 하나라도 변경되면
   `confirmationId`, 음성 일회용 코드, 재인증 증명과 기존 확인 상태를 폐기하고 다시 확인한다.

### 인증 수단의 역할

| 수단 | 증명하는 것 | Backend 검증 | 대체 불가 항목 |
|---|---|---|---|
| `confirmationId` | 현재 확인 화면과 서버의 거래 Snapshot 일치 | 사용자·세션·거래값·만료와 대조 | 본인 인증, 중복 방지 |
| 음성 일회용 코드 | 사용자의 명시적 거래 승인 의사 | 일회용·만료·시도 횟수·거래 바인딩 검증 | PIN·생체인증 기반 재인증 |
| `reauthProof` | 등록 사용자·기기의 본인 재인증 | PIN·생체·Passkey 결과와 거래 바인딩 검증 | 거래 내용 확인, 멱등성 |
| `idempotencyKey` | 같은 실행 요청의 중복 방지 | 사용자 단위 UNIQUE 및 기존 결과 반환 | 확인·인증 |

고정 PIN을 소리 내어 말하게 하지 않는다. 음성으로 읽는 값은 Backend가 발급한 무작위 일회용
코드만 허용하고 원문을 로그에 남기지 않는다.

### 위험 기반 추가 인증 원칙

- 신뢰 기기의 유효한 로그인 세션에서 소액·등록 수취인·낮은 FDS 위험인 경우에는
  `confirmationId`와 음성 일회용 코드로 거래 의사를 확인할 수 있다.
- 신규 수취인, 고액, 비신뢰·변경 기기, 오래된 로그인 세션 또는 정책상 위험 거래에는 음성 확인과
  별도로 PIN·생체인증·Passkey 중 하나의 `reauthProof`를 요구한다.
- `HIGH`·`CRITICAL`처럼 차단 대상으로 결정된 거래는 추가 인증만으로 우회하지 않는다.
- 정확한 금액 기준, 로그인 경과 시간, 위험 등급별 재인증 조건은 보안 정책값으로 확정해야 한다.

### 다음 회의에서 확정할 항목

1. 음성 일회용 코드의 발급·전달 주체와 Streaming 메시지 필드
2. 코드 자릿수, 만료 시간, 최대 시도 횟수 및 재발급 정책
3. PIN·생체인증·Passkey 중 MVP `reauthProof` 방식과 담당 API
4. 신규 수취인·금액·기기·로그인 경과 시간별 추가 인증 기준
5. AI STT가 일회용 숫자를 처리할 때 저장·로그·재사용을 금지하는 규칙
6. `check_balance`를 포함한 최종 Intent 목록과 FDS `decision` 조합

## 1. 결론과 전환 방향

AI의 STT·요구사항 분석·FDS Baseline은 독립 실행 수준까지 구현됐고, Spring Backend도 Voice/FDS
호출과 송금 실행 경로를 갖추고 있다. 팀은 AI의 Intent·Streaming STT·FDS 정책을 외부 계약의
기준으로 삼고 Backend가 Adapter와 실행 기능을 추가하는 방향으로 전환한다.

2026-08-25 합의 방향은 다음과 같다. 아직 구현되지 않은 항목은 현재 API 동작으로 오해하지 않는다.

1. AI는 수취인 별칭과 계좌번호를 모두 추출할 수 있어야 한다. Backend는 별칭 또는 등록된
   계좌번호로 수취인을 찾고, 등록되지 않은 계좌번호는 별도의 일회성 수취인 검증 흐름으로 처리한다.
2. 외부 Intent 이름과 지원 범위는 AI Schema를 기준으로 하고 Backend 내부 명령으로 매핑한다.
3. 음성 전송은 AI의 Streaming STT를 사용한다. Backend는 인증된 WebSocket 중계와 금융 세션
   상관관계를 담당한다.
4. FDS 점수·등급·정책은 AI 모델 정책을 기준으로 한다. AI가 최종 실행 `decision`까지 반환하고
   Backend는 계약 검증 후 실행한다.
5. AI가 지원하지만 Backend에 없는 금융 조회 기능은 Backend가 추가한다. 화면 읽기는 금융 API가
   아니므로 Frontend가 화면 문맥을 제공하고 AI가 읽기 명령을 처리한다.

AI 정책을 따르는 것은 Backend의 소유권·한도·잔액·멱등성·최종 확인 검증을 제거한다는 뜻이 아니다.
AI는 언어 분석과 위험 정책을 소유하고 Backend는 실제 금융 실행의 최종 안전 경계를 유지한다.

## 2. 목표 원칙과 현재 상태

| 항목 | 목표 계약 | 현재 Backend | 상태 |
|---|---|---|---|
| 음성 전달 | Frontend → Backend WebSocket → AI Streaming STT | 요청 단위 multipart | 구현 대기 |
| 송금 수취인 | 별칭 또는 계좌번호 | 등록 별칭만 허용 | 구현 대기 |
| Intent | AI Schema 명칭 | Backend enum 명칭 | Adapter 구현 대기 |
| 출금 계좌 | `source_bank/source_account` 또는 별칭, 미지정 시 기본계좌 | `sourceAccountAlias` 또는 기본계좌 | Mapping 필요 |
| 언어 Context | AI가 Streaming 대화 문맥 관리 | Backend가 슬롯 병합 | 책임 경계 조정 필요 |
| 금융 상태 | Backend가 세션·확인·만료·멱등성 소유 | 구현 완료 | 유지 |
| 확인 | Backend가 확인 문장·`confirmationId`·음성 일회용 코드 생성 | `confirmationId` 구현, 음성 코드 미구현 | 추가 구현 |
| 재인증 | 위험 기반 PIN·생체·Passkey `reauthProof` | 로그인용 PIN 존재, 송금 바인딩 없음 | 정책·API 설계 필요 |
| FDS 정책 | AI가 score/level/decision 결정 | Backend 고정 조합 검증 | 계약 전환 필요 |
| TTS | AI 또는 Frontend 재생, 금융 결과는 Backend `voiceMessage` 유지 | 기기 TTS 계약 | 역할 확정 필요 |
| 실패 정책 | AI 장애·계약 오류 시 Fail-Closed | 구현 완료 | 유지 |

## 3. Voice 계약 차이

### 3.1 Intent

| AI 외부 Intent | Backend 내부 명령 | 조치 |
|---|---|---|
| `transfer_money` | `TRANSFER` | Adapter 변환 후 기존 송금 실행 |
| `check_balance` | `BALANCE` | AI Schema 추가 확인, Backend 음성 잔액조회 연결 |
| `check_history` | `HISTORY` | Backend 음성 거래내역 연결 |
| `check_savings` | `SAVINGS` | Backend 적금 조회 API·내부 명령 추가 |
| `read_screen` | `READ_SCREEN` | Frontend가 화면 문맥 제공, 금융 Backend 실행 없음 |
| `confirm` | `CONFIRM` | 기존 확인 흐름 연결 |
| `deny` | `DENY` | 확인 정보 폐기 후 재입력 정책 결정 |
| `cancel` | `CANCEL` | 세션 종료 |
| `unknown` | `UNKNOWN` | 미지원 명령으로 처리 |

외부 JSON은 AI 명칭을 그대로 사용하고 Backend 경계 Adapter가 내부 명령으로 변환한다. 내부 도메인
enum까지 AI 문자열에 종속시키지는 않는다. 현재 Backend 음성 실행 서비스는 `TRANSFER`만 처리한다.
AI 문서에는 잔액조회 Intent가 없으므로 `check_balance` 추가 여부를 AI팀과 확정해야 한다.

### 3.2 Entity

AI 문서의 송금 Entity:

```text
recipient_name, recipient_bank, recipient_account, amount,
source_bank, source_account
```

목표 입력은 별칭과 계좌번호를 모두 허용한다.

```text
amount,
recipient_alias,
recipient_name, recipient_bank, recipient_account,
source_account_alias, source_bank, source_account
```

수취인 해석 순서는 다음과 같다.

1. `recipient_alias`가 있으면 사용자 소유 등록 수취인에서 찾는다.
2. 계좌번호가 있으면 사용자·은행·계좌번호 해시로 등록 수취인을 찾는다.
3. 등록 수취인이 없으면 은행 코드·계좌번호·예금주를 검증한 일회성 수취인으로 처리한다.
4. 별칭과 계좌번호가 동시에 있고 서로 다른 수취인을 가리키면 확인하지 않고 거부한다.

계좌번호는 AES-GCM 암호문으로 저장되므로 암호문 동등 비교를 할 수 없다. 검색용 HMAC 컬럼과
`(user_id, bank_code, account_num_hash)` 인덱스가 필요하다. 등록되지 않은 계좌번호를 자동으로
별칭에 저장하지 않으며, 전체 계좌번호는 로그·AI 응답·Frontend 확인 화면에 노출하지 않는다.

### 3.3 Context와 후속 발화

- AI는 Streaming 대화의 언어 Context를 관리하고 현재까지 해석한 Intent/Entity를 반환한다.
- Backend는 AI 결과를 금융 세션의 pending 값으로 다시 저장하고 소유권·유효기간을 검증한다.
- AI Context가 유실돼도 Backend의 확정 전 금융 상태와 멱등성 기록은 유지돼야 한다.
- `AWAITING_CONFIRMATION` 이후 값이 바뀌면 Backend가 기존 확인 ID와 멱등성 키를 폐기한다.

즉, AI는 언어적 대화 문맥을 소유하고 Backend는 실행 가능한 금융 상태를 소유한다. 같은 정보를
일부 중복 저장할 수 있지만 실제 송금에 사용되는 최종값은 Backend 검증 결과다.

### 3.4 전송 방식

목표 경로는 AI의 Streaming STT를 유지하면서 Backend를 인증 경계로 두는 방식이다.

```text
Frontend ==WebSocket audio chunks==> Spring Backend
Spring Backend ==stream relay==> AI Voice Streaming API
AI Voice API ==interim/final analysis==> Spring Backend ==events==> Frontend
```

Streaming 자체가 금융 안전성을 낮추지는 않지만 다음 구현이 추가된다.

- WebSocket 연결 시 Access Token 인증과 사용자·`voiceSessionId` 소유권 검증
- 오디오 chunk 순서·크기·전체 길이 제한과 backpressure
- interim transcript는 화면 표시만 하고 금융 실행에는 final 결과만 사용
- 연결 중단·재연결·AI timeout·중복 final event 처리
- Backend와 AI 간 상관관계 ID 및 메시지 순서 번호
- 다중 인스턴스 배포 시 sticky session 또는 외부 세션 저장소

현재 multipart API는 Streaming 전환이 완료될 때까지 fallback으로 유지한다. AI에 Frontend Access
Token이나 금융 인증정보를 직접 전달하지 않는다.

### 3.5 최종 확인과 재인증

확인 대기 응답을 받은 Frontend는 같은 응답의 `confirmationId`와 새 UUID
`idempotencyKey`를 최종 확인 발화에 함께 보낸다.

```text
audio: 확인 발화
confirmationId: Backend가 발급한 확인 ID
idempotencyKey: Frontend가 생성한 UUID
```

Backend는 `CONFIRM` Intent일 때 서버 세션의 확인 ID와 요청 값을 대조한다. 누락·불일치하면
`VOICE_4010`으로 거부하고 FDS·오픈뱅킹을 호출하지 않는다. `CANCEL`에는 두 값을 요구하지 않는다.

목표 흐름에서는 확인 문장을 읽은 뒤 Backend가 거래 Snapshot에 묶인 음성 일회용 코드를 발급하고,
사용자가 이를 음성으로 따라 읽는다. Backend는 AI의 `final` 인식 결과만으로 코드를 검증한다.
권장 기본값은 6자리 무작위 숫자, 60~120초 만료, 1회 사용, 최대 3회 시도다. 정확한 값은 공동
정책으로 확정한다.

이 코드는 거래 의사 확인 수단이며 본인 재인증 증명이 아니다. 위험 기반 추가 인증이 필요한 경우
Frontend가 PIN·생체·Passkey 검증을 완료하고 Backend가 발급한 거래 바인딩 `reauthProof`를 함께
보내야 한다. 금액·수취인·출금 계좌 변경 시 확인 ID, 음성 코드와 재인증 증명을 모두 무효화한다.

## 4. FDS 계약 차이

### 4.1 입력

AI Baseline은 AIHub 원본 컬럼 중심으로 추론한다.

```text
출금/입금 금융회사 일련번호, 자금구분, 거래금액, 거래시간대, 매체구분, 거래일자
```

Backend는 AI 정책이 요구하는 원천값 중 서비스에서 신뢰할 수 있게 수집 가능한 값을 전달한다.

```text
requestId, transferId, userId, amount, balanceBefore, requestedAt,
recipient.transferCount/firstTime,
profile.coldStart/30일 금액·횟수·수취인·시간대 집계,
context.trustedDevice/sttConfidence
```

AI 서버는 학습 Feature와 서비스 Feature의 매핑표를 만들고, 동일한 전처리 코드로 변환해야 한다.
AI 정책에 금융회사·자금구분·매체구분이 필수라면 Backend 요청 DTO에 명시적으로 추가한다.
어느 파트도 누락 Feature를 임의 기본값으로 채우지 않는다.

### 4.2 출력

| 항목 | AI 정책 | Backend 전환 작업 |
|---|---|---|
| 위험 점수 | `risk_score` 0~100 | validator 범위 변경 |
| 위험 등급 | LOW/MEDIUM/HIGH/CRITICAL | `CRITICAL` 추가 |
| 이상 판정 | `is_fraud` | 결과 저장 필드 추가 |
| 실행 결정 | AI 정책이 결정 | `decision` 필수 계약 유지 |
| 모델 상세 | `anomaly_score`, `threshold`, `decision_margin` | 응답·저장 DTO 추가 |
| 추적 | AI 문서에 없음 | 요청과 같은 `requestId` 유지 |
| 버전 | Model Bundle 내부 | `modelVersion`, `policyVersion` 응답 추가 요청 |
| 설명 | 모델 값 중심 | 사용자/운영용 `reasonCodes` 추가 요청 |
| 지연시간 | 문서에 없음 | `latencyMs` 추가 요청 |

AI가 최종 `decision`을 반환해야 한다. Backend가 `risk_score`나 `is_fraud`만 보고 정책을 다시
추론하면 AI 정책을 따른다는 원칙과 충돌한다. 목표 응답은 다음 실행 결정을 포함한다.

```text
LOW + ALLOW
MEDIUM + ALLOW_WITH_ALERT
HIGH + BLOCK
CRITICAL + BLOCK
```

정확한 등급 경계와 `is_fraud`·`decision` 조합은 AI 정책 버전으로 고정한다. Backend는 알려진
등급·결정인지, 점수 범위와 request ID가 유효한지만 검증하며 계약 오류·timeout은 계속 Fail-Closed
처리한다. 위험 점수는 사기 확률로 표시하지 않는다.

## 5. AI 태스크 8~18 연동 상태

| 번호 | AI 태스크 | Backend 상태 | 담당/다음 조치 |
|---:|---|---|---|
| 8 | 출금 계좌 선택 | 계좌 목록·기본계좌·별칭 선택 구현 | AI source 필드와 Backend 계좌 매핑 추가 |
| 9 | Confirmation Builder | Backend 구현 완료 | AI 중복 구현 불필요 |
| 10 | TTS | `voiceMessage` 제공 | Frontend 기기 TTS 담당 |
| 11 | Confirm/Deny/Cancel | CONFIRM/CANCEL 구현 | DENY 내부 상태와 재입력 정책 추가 |
| 12 | Voice Pipeline 통합 | multipart HTTP Client 구현 | WebSocket 중계와 Streaming 계약 추가 |
| 13 | 화면 읽기 | Backend 금융 API 없음 | Frontend가 화면 문맥 제공, AI가 읽기 처리 |
| 14 | 거래내역 조회 | REST API 구현, 음성 연결 없음 | Backend 음성 HISTORY 분기와 Entity 매핑 필요 |
| 15 | 적금 조회 | 도메인/API 없음 | Backend 적금 조회 API 신규 구현 |
| 16 | Fraud Detection | 호출·검증·차단 구현 | AI 점수·CRITICAL·decision 계약으로 전환 |
| 17 | Node/Spring 규격 | REST 계약 존재, WebSocket 없음 | Streaming WebSocket 공동 설계·구현 |
| 18 | E2E | 단위·통합 테스트 일부 | 세 파트 staging 준비 후 종단 테스트 |

## 6. 담당별 작업 목록

### AI

- 별칭과 계좌번호를 함께 처리하는 Voice Intent/Entity Schema 확정
- `check_balance` 포함 여부와 모든 Intent 이름 확정
- Streaming Voice 메시지 Schema와 실행 가능한 staging 제공
- 언어 Context를 관리하되 Backend 확인·실행 상태를 덮어쓰지 않음
- Baseline Feature와 서비스 Feature 매핑
- FDS score/level/is_fraud와 최종 `decision` 정책 확정
- FDS 응답에 request/model/policy version, reason, latency 추가

### Frontend

- 인증된 Backend WebSocket으로 오디오 chunk 전송
- interim/final transcript와 상태 event 처리
- 확인 대기 응답에서 `confirmationId` 보관
- 확인 발화에 같은 `confirmationId`와 UUID `idempotencyKey` 전송
- Backend가 음성 일회용 코드를 발급하면 사용자에게 안내하고 최종 인식 결과 전송
- 위험 기반 추가 인증 요청 시 PIN·생체·Passkey UI를 실행하고 `reauthProof` 전달
- Backend `voiceMessage`를 기기 TTS로 재생
- 네트워크 결과 불명확 시 새 키 생성 없이 상태 조회

### Backend

- [x] `confirmationId` 누락·불일치 차단 (#66)
- 거래 Snapshot에 바인딩된 음성 일회용 코드 발급·만료·1회 검증 추가
- 위험 조건 판정과 거래 바인딩 `reauthProof` 검증 API 추가
- 계좌번호 HMAC 검색 컬럼과 등록 수취인 조회 추가
- 등록되지 않은 계좌번호의 일회성 수취인·예금주 검증 흐름 추가
- AI Intent Adapter와 `BALANCE`, `HISTORY`, `SAVINGS`, `DENY` 실행 분기 추가
- 인증·길이 제한·backpressure를 포함한 Voice WebSocket 중계 추가
- AI FDS 점수 범위·`CRITICAL`·최종 decision 계약으로 DTO/validator 전환
- Voice/FDS staging URL과 운영 설정 검증
- AI 오류·타임아웃 Fail-Closed 유지
- 실제 Voice → FDS → OpenBanking E2E 작성

## 7. 연동 완료 체크리스트

- [ ] Voice Streaming 계약과 OpenAPI/AsyncAPI가 합의 내용과 일치한다.
- [ ] 등록 수취인 별칭 송금 시나리오가 통과한다.
- [ ] 등록 계좌번호 발화가 같은 수취인으로 안전하게 해석된다.
- [ ] 미등록 계좌번호가 예금주 확인과 최종 사용자 확인 없이 실행되지 않는다.
- [ ] AI 언어 Context와 Backend 금융 상태의 복구·불일치 규칙이 검증된다.
- [x] 확인 ID 누락·불일치 시 송금 실행 경로가 호출되지 않는다.
- [ ] 음성 일회용 코드가 거래 Snapshot에 묶이고 만료·재사용·시도 초과가 차단된다.
- [ ] 음성 확인만으로 재인증 완료 상태가 되지 않는다.
- [ ] 위험 거래는 유효한 거래 바인딩 `reauthProof` 없이 실행되지 않는다.
- [ ] 금액·수취인·출금 계좌 변경 시 확인 ID·음성 코드·재인증 증명이 모두 폐기된다.
- [ ] FDS score 0~100, CRITICAL, risk/decision 조합이 AI 정책 버전과 일치한다.
- [ ] FDS 타임아웃·잘못된 응답에서 이체가 실행되지 않는다.
- [ ] 같은 멱등성 키의 재요청이 이체 한 건으로 수렴한다.
- [ ] Frontend 기기 TTS 실패가 금융 결과를 변경하지 않는다.
- [ ] staging에서 Voice → FDS → 이체/차단 시나리오를 검증한다.
