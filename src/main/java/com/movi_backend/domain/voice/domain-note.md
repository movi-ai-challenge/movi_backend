# voice 도메인

음성 세션과 명령을 다룬다. STT·NLU는 AI 파트가 하고, **이 패키지는 그 결과를 검증하고 실행을 판단하는 쪽**이다.

도메인 전반의 불변식은 [docs/domain-guide.md](../../../../../../docs/domain-guide.md), 파트 간 계약은 [docs/integration-spec.md](../../../../../../docs/integration-spec.md) 6절이 기준이다. 이 문서는 패키지 내부 관점의 보충 설명이다.

## 책임

| 클래스 | 역할 |
|---|---|
| `VoiceSessionService` | 세션 시작 |
| `VoiceCommandService` | 발화 1건의 전체 처리. 검증 → 의도 분기 → 응답 |
| `VoiceSessionExpirationService` | 만료 처리를 별도 트랜잭션으로 분리 |
| `AudioDurationValidator` | 업로드 음성의 재생 시간 검증 |
| `VoiceAnalysisClient` | AI Voice API 경계. Mock/HTTP 구현 교체 |

## 지켜야 할 것

### AI 응답은 신뢰 경계 밖 입력이다

`VoiceAnalysisResponse`의 어떤 필드도 그대로 쓰지 않는다. intent·confidence·entity를 모두 재검증하고, 값이 비면 추측해서 채우지 말고 재질문한다. 이 원칙이 깨지면 AI 환각이 곧 실제 이체가 된다.

### 세션이 슬롯의 단일 소유자다

프론트와 AI는 슬롯을 보관하지 않는다. 저장·병합·폐기를 `VoiceSession`이 전부 책임진다.

만료·취소·완료·의도 전환 시 **슬롯을 전부 폐기한다. 일부만 살리지 않는다.** 옛 슬롯이 남아 뒤이은 발화와 병합되면 사용자가 의도하지 않은 이체가 나간다.

### 의도별 분기

`VoiceCommandService.process()`가 상태와 의도로 갈라진다.

```text
AWAITING_CONFIRMATION → CONFIRM/CANCEL만 수신 (그 외 INVALID_SESSION_STATE)
HISTORY               → 조회 후 ACTIVE 로 복귀 (확인 단계 없음)
BALANCE               → 조회 후 ACTIVE 로 복귀 (확인 단계 없음)
TRANSFER              → 검증 → 재질문 또는 확인 대기
그 외                 → INTENT_UNKNOWN
```

`PROCESSING` 중에는 확인 발화를 다시 받지 않는다. 중복 이체를 막는 1차 방어선이다.

### 조회는 확인 단계를 두지 않는다

돈이 움직이지 않으므로 `HISTORY`·`BALANCE`는 바로 답한다. 대신 조회 뒤 세션을 `ACTIVE`로 되돌려 이어지는 명령을 받을 수 있게 한다. 이때 앞선 송금 슬롯이 남아 있으면 폐기한다 — 사용자가 화제를 바꾼 것이기 때문이다.

다만 **계좌 별칭은 송금과 같은 신뢰도 기준으로 검증한다.** 잘못 들으면 엉뚱한 계좌 잔액을 읽어 주는데, 화면으로 확인할 수 없는 사용자에게는 정정할 방법이 없다.

### 조회 실패를 0원으로 안내하지 않는다

오픈뱅킹은 실패할 때도 성공과 같은 스키마로 응답하며 금액 필드를 `0`으로 채워 보낸다. 이를 그대로 읽으면 사용자는 "통장에 영원 있어요"를 사실로 믿는다. 조회 실패는 반드시 실패로 안내한다.

### 음성으로 읽을 수 있는 분량만 응답한다

목록을 스무 건씩 읽어 주면 듣는 사람이 따라올 수 없다. `HISTORY`는 최근 3건만 읽고 나머지는 총 건수로 알린다. 금액은 반드시 `KoreanMoneyFormatter`를 거친다 — TTS가 `53000원`을 어떻게 읽을지 보장할 수 없다.

### 로그·응답에 원문을 남기지 않는다

`VoiceCommand`에 저장하는 `sttText`와 `entities`는 `SensitiveTextMasker`를 거친다. 발화에 계좌번호가 섞여 들어오기 때문이다.

## 변경 이력

- **2026-08-27** — Safari/iOS 녹음 파일 지원 추가 (#91). `audio/mp4`·`audio/x-m4a`를 허용하고, MIME 문자열만 믿지 않도록 MP4의 `ftyp`·`moov`·`mvhd` box를 파싱해 버전 0·1 재생시간을 검증한다. 15초 초과와 손상된 box 크기·재생시간 누락은 AI 호출 전에 거부한다.
- **2026-08-25** — `BALANCE` 의도 처리 추가 (#73). `HISTORY`와 같은 조회 흐름을 따르며 `BalanceInquiryService`를 그대로 호출한다. `VoiceCommandResponse`에 `balance` 필드 추가.
- **2026-08-25** — `HISTORY` 의도 처리 추가 (#68). `validateIntent()`가 `TRANSFER` 외를 전부 거부해 "거래내역 알려줘"가 음성으로 동작하지 않던 문제를 해결했다. 함께 바뀐 것:
  - `VoiceSessionStatus`에 `CLARIFYING → ACTIVE` 전이 추가 (의도 전환)
  - `VoiceSession.resumeActive()` 추가 — 슬롯을 폐기하고 명령 대기로 복귀
  - `VoiceCommandResponse`에 `history` 필드 추가 (송금 응답에는 `null`)
  - `VoiceHistoryPeriod` — AI가 준 기간을 재검증하고 빈 값을 기본 기간으로 채운다
  - `ErrorCode.HISTORY_PERIOD_INVALID`(VOICE_4010) 추가
