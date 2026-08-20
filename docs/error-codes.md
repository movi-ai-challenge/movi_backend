# 에러 코드 정의서

구현체: `com.movi_backend.global.error.ErrorCode`
관련 문서: [CLAUDE.md](../CLAUDE.md) · [domain-guide.md](domain-guide.md)

---

## 1. 코드 체계

```text
{도메인}_{HTTP상태 3자리}{일련번호 1자리}

예) AUTH_4010  → 인증 도메인 / 401 / 0번
    TRANSFER_4001 → 이체 도메인 / 400 / 1번
    FDS_5000   → FDS 도메인 / 500 / 0번
```

| 도메인 접두어 | 범위 |
|---|---|
| `AUTH` | 로그인, PIN·생체인증, 토큰 |
| `KAKAO` | 카카오 OAuth 외부 통신 |
| `ACCOUNT` | 계좌 등록·조회·기본계좌 |
| `OPENBANK` | 오픈뱅킹 API 외부 통신 |
| `TRANSFER` | 이체 실행, 수취인, 한도 |
| `VOICE` | 음성 인식, 슬롯 필링, 재질문 |
| `FDS` | 이상거래 평가 |
| `GUARDIAN` | 보호자 연결·권한 |
| `NOTI` | 알림 발송 |
| `REQ` / `SRV` | 공통 요청 오류 / 서버 오류 |

---

## 2. 응답 형식

```json
{
  "code": "TRANSFER_4001",
  "message": "잔액이 부족합니다.",
  "voiceMessage": "잔액이 부족해요. 다른 금액을 말씀해 주세요."
}
```

**`message`와 `voiceMessage`를 분리한 이유**

이 서비스에서 에러는 화면에 찍히고 끝나는 게 아니라 **TTS로 읽힙니다**. 화면용 문구와 음성용 문구는 요구사항이 다릅니다.

| | `message` | `voiceMessage` |
|---|---|---|
| 대상 | 개발자·로그·화면 | 시각장애인·시니어 사용자 |
| 문체 | 간결한 서술 | 구어체, 다음 행동 안내 포함 |
| 예시 | "잔액이 부족합니다." | "잔액이 부족해요. 다른 금액을 말씀해 주세요." |

### voiceMessage 작성 원칙

1. **한 문장, 짧게** — 길면 듣는 도중에 잊습니다
2. **다음 행동을 알려준다** — "안 됩니다"로 끝내지 말고 "~해 주세요"까지
3. **기술 용어 금지** — "토큰", "세션", "API", "서버" 같은 단어를 쓰지 않습니다
4. **구어체 종결** — "~해요", "~해 주세요". 시니어 대상이라 딱딱한 문어체를 피합니다
5. **숫자는 읽기 쉽게** — "50000원"이 아니라 "5만원"

---

## 3. 전체 코드 목록

### AUTH — 인증

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| AUTH_4010 | `UNAUTHORIZED` | 401 | 인증이 필요합니다. | 로그인이 필요해요. |
| AUTH_4011 | `INVALID_ACCESS_TOKEN` | 401 | 유효하지 않은 액세스 토큰입니다. | 다시 로그인해 주세요. |
| AUTH_4012 | `EXPIRED_ACCESS_TOKEN` | 401 | 액세스 토큰이 만료되었습니다. | 로그인 시간이 지났어요. 다시 로그인해 주세요. |
| AUTH_4013 | `INVALID_REFRESH_TOKEN` | 401 | 리프레시 토큰이 유효하지 않습니다. | 다시 로그인해 주세요. |
| AUTH_4014 | `INVALID_OAUTH_STATE` | 401 | 로그인 요청 상태가 유효하지 않습니다. | 로그인을 처음부터 다시 시도해 주세요. |
| AUTH_4020 | `PIN_MISMATCH` | 401 | 비밀번호가 일치하지 않습니다. | 비밀번호가 맞지 않아요. 다시 입력해 주세요. |
| AUTH_4021 | `PIN_LOCKED` | 403 | 비밀번호 입력 제한 횟수를 초과했습니다. | 비밀번호를 여러 번 잘못 입력하셨어요. 잠시 후 다시 시도해 주세요. |
| AUTH_4022 | `PIN_NOT_REGISTERED` | 400 | 등록된 비밀번호가 없습니다. | 비밀번호를 먼저 등록해 주세요. |
| AUTH_4090 | `PIN_ALREADY_REGISTERED` | 409 | 비밀번호가 이미 등록되어 있습니다. | 비밀번호가 이미 등록되어 있어요. |
| AUTH_4023 | `BIOMETRIC_NOT_ENABLED` | 400 | 생체인증이 설정되어 있지 않습니다. | 지문이나 얼굴 인식이 설정되어 있지 않아요. |
| AUTH_4030 | `FORBIDDEN` | 403 | 접근 권한이 없습니다. | 이 기능을 사용할 수 없어요. |
| AUTH_4040 | `USER_NOT_FOUND` | 404 | 회원을 찾을 수 없습니다. | 회원 정보를 찾을 수 없어요. |

### KAKAO — 카카오 로그인

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| KAKAO_4000 | `KAKAO_TOKEN_IS_BLANK` | 400 | 카카오 토큰이 비어 있습니다. | 로그인에 실패했어요. 다시 시도해 주세요. |
| KAKAO_4001 | `KAKAO_AUTHORIZATION_FAILED` | 400 | 카카오 인가 처리에 실패했습니다. | 카카오 로그인을 처음부터 다시 시도해 주세요. |
| KAKAO_4002 | `KAKAO_REQUIRED_INFO_MISSING` | 400 | 카카오 필수 회원 정보가 없습니다. | 전화번호 제공에 동의한 뒤 다시 로그인해 주세요. |
| KAKAO_5000 | `KAKAO_COMMUNICATION_ERROR` | 502 | 카카오 통신에 실패하였습니다. | 카카오 로그인이 지금 안 돼요. 잠시 후 다시 시도해 주세요. |

### ACCOUNT — 계좌

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| ACCOUNT_4001 | `ACCOUNT_ALREADY_REGISTERED` | 400 | 이미 등록된 계좌입니다. | 이미 등록된 계좌예요. |
| ACCOUNT_4002 | `ACCOUNT_INACTIVE` | 400 | 사용할 수 없는 계좌입니다. | 사용할 수 없는 계좌예요. 다른 계좌를 선택해 주세요. |
| ACCOUNT_4003 | `ACCOUNT_ALIAS_DUPLICATED` | 400 | 이미 사용 중인 계좌 별칭입니다. | 같은 이름의 계좌가 이미 있어요. 다른 이름을 말씀해 주세요. |
| ACCOUNT_4004 | `PRIMARY_ACCOUNT_NOT_SET` | 400 | 기본 계좌가 설정되어 있지 않습니다. | 주로 쓰실 계좌를 먼저 정해 주세요. |
| ACCOUNT_4040 | `ACCOUNT_NOT_FOUND` | 404 | 계좌를 찾을 수 없습니다. | 말씀하신 계좌를 찾을 수 없어요. |

### OPENBANK — 오픈뱅킹 연동

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| OPENBANK_4001 | `INVALID_FINTECH_USE_NUM` | 400 | 유효하지 않은 핀테크이용번호입니다. | 계좌 정보에 문제가 있어요. 계좌를 다시 연결해 주세요. |
| OPENBANK_4002 | `INVALID_OPENBANKING_STATE` | 400 | 유효하지 않은 계좌 연결 요청입니다. | 계좌 연결에 실패했어요. 처음부터 다시 시도해 주세요. |
| OPENBANK_4010 | `CONNECTION_EXPIRED` | 401 | 오픈뱅킹 연결이 만료되었습니다. | 은행 연결이 끊어졌어요. 다시 연결해 주세요. |
| OPENBANK_4040 | `CONNECTION_NOT_FOUND` | 404 | 오픈뱅킹 연결 정보를 찾을 수 없습니다. | 은행 계좌가 연결되어 있지 않아요. |
| OPENBANK_5000 | `OPENBANK_COMMUNICATION_ERROR` | 502 | 오픈뱅킹 통신에 실패하였습니다. | 은행과 연결이 잠시 안 돼요. 조금 뒤에 다시 시도해 주세요. |
| OPENBANK_5001 | `BALANCE_INQUIRY_FAILED` | 502 | 잔액 조회에 실패했습니다. | 잔액을 확인하지 못했어요. 다시 말씀해 주세요. |
| OPENBANK_5002 | `TRANSFER_EXECUTION_FAILED` | 502 | 이체 실행에 실패했습니다. | 송금하지 못했어요. 돈은 빠져나가지 않았어요. |

> `INVALID_OPENBANKING_STATE`는 계좌 연결 CSRF 방어 실패 시 반환합니다. 카카오 로그인용 `AUTH_4014`(`INVALID_OAUTH_STATE`)와 구분됩니다. **왜 실패했는지 사용자에게 설명하지 않습니다** — 공격 시도라면 공격자에게 정보를 주게 되기 때문입니다.
>
> `TRANSFER_EXECUTION_FAILED`의 음성 문구에 **"돈은 빠져나가지 않았어요"** 를 넣은 이유 — 화면을 못 보는 사용자에게 가장 불안한 상황이 "실패했는데 돈이 나갔는지 모르는" 경우입니다.

### TRANSFER — 이체

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| TRANSFER_4001 | `INSUFFICIENT_BALANCE` | 400 | 잔액이 부족합니다. | 잔액이 부족해요. 다른 금액을 말씀해 주세요. |
| TRANSFER_4002 | `INVALID_AMOUNT` | 400 | 유효하지 않은 이체 금액입니다. | 금액을 다시 말씀해 주세요. |
| TRANSFER_4003 | `AMOUNT_LIMIT_EXCEEDED` | 400 | 1회 이체 한도를 초과했습니다. | 한 번에 보낼 수 있는 금액을 넘었어요. |
| TRANSFER_4004 | `DAILY_LIMIT_EXCEEDED` | 400 | 1일 이체 한도를 초과했습니다. | 오늘 보낼 수 있는 금액을 모두 쓰셨어요. |
| TRANSFER_4005 | `SELF_TRANSFER_NOT_ALLOWED` | 400 | 본인 계좌로는 이체할 수 없습니다. | 같은 계좌로는 보낼 수 없어요. |
| TRANSFER_4006 | `INVALID_STATUS_TRANSITION` | 400 | 처리할 수 없는 이체 상태입니다. | 이미 처리된 송금이에요. |
| TRANSFER_4031 | `TRANSFER_BLOCKED` | 403 | 위험 거래로 차단된 이체입니다. | 안전을 위해 이번 송금을 멈췄어요. 보호자에게 알려 드렸어요. |
| TRANSFER_4040 | `TRANSFER_NOT_FOUND` | 404 | 이체 내역을 찾을 수 없습니다. | 송금 내역을 찾을 수 없어요. |
| TRANSFER_4041 | `RECIPIENT_NOT_FOUND` | 404 | 등록된 수취인을 찾을 수 없습니다. | 그런 이름으로 저장된 분이 없어요. 다시 말씀해 주세요. |
| TRANSFER_4090 | `DUPLICATE_TRANSFER` | 409 | 이미 처리 중인 이체 요청입니다. | 방금 같은 송금을 요청하셨어요. 잠시만 기다려 주세요. |

### VOICE — 음성 인식·재질문

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| VOICE_4001 | `AMOUNT_MISSING` | 400 | 이체 금액이 누락되었습니다. | **얼마를 보내시겠어요?** |
| VOICE_4002 | `RECIPIENT_MISSING` | 400 | 수취인이 누락되었습니다. | **누구에게 보내시겠어요?** |
| VOICE_4003 | `INTENT_UNKNOWN` | 400 | 명령 의도를 파악하지 못했습니다. | 무엇을 도와드릴까요? 잔액 조회나 송금이라고 말씀해 주세요. |
| VOICE_4004 | `LOW_CONFIDENCE` | 400 | 음성 인식 신뢰도가 낮습니다. | 잘 못 들었어요. 다시 한번 말씀해 주세요. |
| VOICE_4005 | `SLOT_EXPIRED` | 400 | 대화 세션이 만료되었습니다. | 시간이 좀 지났어요. 처음부터 다시 말씀해 주세요. |
| VOICE_4006 | `RETRY_LIMIT_EXCEEDED` | 400 | 음성 인식 재시도 횟수를 초과했습니다. | 음성 인식이 잘 안 되네요. 잠시 후 다시 시도해 주세요. |
| VOICE_4007 | `INVALID_SESSION_STATE` | 400 | 처리할 수 없는 음성 세션 상태입니다. | 지금은 처리할 수 없어요. 처음부터 다시 말씀해 주세요. |
| VOICE_4040 | `VOICE_SESSION_NOT_FOUND` | 404 | 음성 세션을 찾을 수 없습니다. | 처음부터 다시 말씀해 주세요. |
| VOICE_5000 | `STT_FAILED` | 502 | 음성 인식에 실패했습니다. | 소리를 알아듣지 못했어요. 다시 말씀해 주세요. |
| VOICE_5001 | `TTS_FAILED` | 502 | 음성 합성에 실패했습니다. | — (음성 출력 자체가 실패한 상황이므로 화면·진동으로 대체) |

> `AMOUNT_MISSING` · `RECIPIENT_MISSING`의 문구는 **MVP 기능명세서에 명시된 재질문 문구를 그대로** 사용했습니다. 임의로 바꾸지 마세요.
>
> 다만 **공개 Voice API에서 슬롯 누락은 에러가 아니라 대화의 정상 분기**입니다. `200`과 함께
> `state: CLARIFYING`으로 응답합니다([integration-spec.md](integration-spec.md) 5.3절).
> 이 두 에러 코드는 직접 실행 API나 내부 검증 실패에만 사용합니다.

### FDS — 이상거래 탐지

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| FDS_4031 | `HIGH_RISK_BLOCKED` | 403 | 고위험 거래로 차단되었습니다. | 안전을 위해 이번 송금을 멈췄어요. 보호자에게 알려 드렸어요. |
| FDS_5000 | `ASSESSMENT_FAILED` | 502 | 위험도 평가에 실패했습니다. | 안전 확인을 하지 못해 송금을 진행하지 않았어요. 잠시 후 다시 시도해 주세요. |
| FDS_5001 | `ASSESSMENT_TIMEOUT` | 504 | 위험도 평가 응답이 지연되었습니다. | 안전 확인이 늦어지고 있어요. 잠시 후 다시 시도해 주세요. |

> **`ASSESSMENT_FAILED`는 이체를 통과시키지 않습니다.** 평가 불가는 곧 위험입니다. 음성 문구에도 "송금을 진행하지 않았어요"를 명시해 사용자가 상태를 오해하지 않게 합니다.

### GUARDIAN — 보호자

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| GUARDIAN_4001 | `ALREADY_LINKED` | 400 | 이미 연결된 보호자입니다. | 이미 연결된 분이에요. |
| GUARDIAN_4002 | `INVITE_EXPIRED` | 400 | 초대 링크가 만료되었습니다. | 초대가 만료됐어요. 다시 요청해 주세요. |
| GUARDIAN_4003 | `INVALID_INVITE_TOKEN` | 400 | 유효하지 않은 초대 링크입니다. | 초대 정보가 올바르지 않아요. |
| GUARDIAN_4004 | `SELF_LINK_NOT_ALLOWED` | 400 | 본인을 보호자로 등록할 수 없습니다. | 본인은 보호자로 등록할 수 없어요. |
| GUARDIAN_4030 | `GUARDIAN_NO_PERMISSION` | 403 | 보호자 권한이 없습니다. | 이 정보를 볼 권한이 없어요. |
| GUARDIAN_4040 | `GUARDIAN_LINK_NOT_FOUND` | 404 | 보호자 연결 정보를 찾을 수 없습니다. | 연결된 보호자가 없어요. |

### NOTI — 알림

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| NOTI_4001 | `INVALID_PHONE_NUMBER` | 400 | 유효하지 않은 전화번호 형식입니다. | 전화번호가 올바르지 않아요. 다시 말씀해 주세요. |
| NOTI_5000 | `SMS_SEND_FAILED` | 502 | SMS 전송에 실패했습니다. | 문자를 보내지 못했어요. |

### REQ / SRV — 공통

| 코드 | Enum | HTTP | message | voiceMessage |
|---|---|---|---|---|
| REQ_4000 | `BAD_REQUEST` | 400 | 잘못된 요청입니다. | 요청을 처리하지 못했어요. 다시 시도해 주세요. |
| REQ_4050 | `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드입니다. | 요청을 처리하지 못했어요. |
| REQ_4150 | `UNSUPPORTED_MEDIA_TYPE` | 415 | 지원하지 않는 미디어 타입입니다. | 요청을 처리하지 못했어요. |
| SRV_4040 | `NOT_FOUND` | 404 | 찾을 수 없습니다. | 찾을 수 없어요. |
| SRV_5000 | `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류가 발생했습니다. | 문제가 생겼어요. 잠시 후 다시 시도해 주세요. |
| SRV_5030 | `SERVICE_UNAVAILABLE` | 503 | 현재 서비스를 사용할 수 없습니다. | 지금은 서비스를 이용할 수 없어요. 잠시 후 다시 시도해 주세요. |

---

## 4. 사용 규칙

**새 에러 코드를 추가할 때**

1. 기존 코드로 표현 가능한지 먼저 확인합니다 — 무분별한 추가는 프론트·AI 파트의 분기 처리를 늘립니다
2. 도메인 접두어와 HTTP 상태를 먼저 정하고, 일련번호는 그 안에서 가장 큰 값 +1
3. `voiceMessage`를 반드시 채웁니다. 작성 원칙 5가지를 지킵니다
4. 이 문서와 `ErrorCode.java`를 **함께** 갱신합니다

**던지는 쪽**

```java
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);

// 로그에 남길 상세 정보가 필요한 경우 (사용자 응답에는 노출되지 않음)
throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND, "accountId=" + accountId);
```

`detailMessage`에 **계좌번호·전화번호·토큰을 넣지 마세요.** 로그에 그대로 남습니다.
