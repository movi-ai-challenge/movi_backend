# API 응답 규약

구현체: `com.movi_backend.global.response.ApiResponse` · `PageResponse`
관련 문서: [error-codes.md](error-codes.md)

---

## 1. 기본 형식

**모든 API는 성공·실패 여부와 무관하게 동일한 구조로 응답합니다.**

```json
{
  "code": "SUCCESS",
  "message": "요청이 정상 처리되었습니다.",
  "voiceMessage": "국민은행 통장에 5만 3천원 있어요.",
  "data": { "balance": 53000, "bankName": "국민은행" }
}
```

실패도 같은 구조입니다. `data`만 `null`입니다.

```json
{
  "code": "TRANSFER_4001",
  "message": "잔액이 부족합니다.",
  "voiceMessage": "잔액이 부족해요. 다른 금액을 말씀해 주세요.",
  "data": null
}
```

| 필드 | 설명 |
|---|---|
| `code` | 성공 시 `"SUCCESS"`, 실패 시 에러 코드 (`TRANSFER_4001` 등) |
| `message` | 화면 표시·로그용 문구 |
| `voiceMessage` | **TTS로 읽을 문구.** 음성 안내가 불필요한 응답은 `null` |
| `data` | 실제 데이터. 실패 시 항상 `null` |

**구조를 통일한 이유** — 클라이언트가 하나의 파서로 두 경우를 모두 처리하고, `code`로만 분기하면 됩니다. HTTP 상태 코드는 그대로 유지되므로 그것으로 판단해도 됩니다.

---

## 2. voiceMessage 채우는 기준

화면을 보지 못하는 사용자에게 `voiceMessage`는 **유일한 피드백 수단**입니다. 다만 모든 응답에 넣으면 오히려 시끄럽습니다.

| 상황 | voiceMessage | 예시 |
|---|---|---|
| 사용자에게 결과를 알려야 함 | **필수** | 잔액 조회, 이체 완료, 보호자 연결 성공 |
| 화면 렌더링만 필요 | `null` | 거래내역 목록, 계좌 목록 |
| 모든 에러 | **필수** (ErrorCode에 정의됨) | 잔액 부족, 인식 실패 |

### 작성 원칙

[error-codes.md](error-codes.md)의 원칙과 동일합니다.

1. 한 문장, 짧게
2. 다음 행동을 알려준다
3. 기술 용어 금지
4. 구어체 종결 (`~해요`, `~해 주세요`)
5. **숫자는 읽기 쉽게** — `"53000원"`이 아니라 `"5만 3천원"`

> 5번이 특히 중요합니다. TTS 엔진이 `53000원`을 "오만삼천원"으로 읽을지 "오삼공공공원"으로 읽을지 보장할 수 없습니다. **서버에서 한국어 표기로 변환해 내려보내세요.**

---

## 3. 사용 예시

```java
// 음성 안내가 필요한 조회
@GetMapping("/balance")
public ApiResponse<BalanceResponse> getBalance(...) {
    final BalanceResponse balance = balanceService.inquire(...);
    return ApiResponse.success(balance, balance.toVoiceMessage());
}

// 화면 표시만 필요한 목록
@GetMapping("/transactions")
public ApiResponse<PageResponse<TransactionResponse>> getTransactions(...) {
    return ApiResponse.success(transactionService.findAll(...));
}

// 데이터 없이 음성 안내만
@PostMapping("/guardians")
public ApiResponse<Void> linkGuardian(...) {
    guardianService.requestLink(...);
    return ApiResponse.successWithVoice("보호자에게 문자를 보냈어요.");
}

// 데이터도 음성도 없음
@DeleteMapping("/recipients/{id}")
public ApiResponse<Void> deleteRecipient(...) {
    recipientService.delete(...);
    return ApiResponse.success();
}
```

**에러는 던지기만 합니다.** `GlobalExceptionHandler`가 같은 형식으로 변환합니다.

```java
throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
```

---

## 4. 목록 응답

목록은 `PageResponse<T>`로 통일합니다. 3명이 각자 다른 페이징 형식을 만들면 프론트가 API마다 다르게 파싱해야 합니다.

```json
{
  "code": "SUCCESS",
  "message": "요청이 정상 처리되었습니다.",
  "voiceMessage": null,
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "hasNext": true
  }
}
```

```java
PageResponse.of(content, page, size, totalElements);
```

> Spring Data `Page`를 변환하는 팩토리는 JPA 의존성 추가 후 넣습니다.

---

## 5. HTTP 상태 코드

응답 바디와 별개로 HTTP 상태 코드는 정확히 내려보냅니다.

| 상황 | 상태 코드 |
|---|---|
| 조회·수정 성공 | 200 |
| 생성 성공 | 201 |
| 클라이언트 오류 | 400 / 401 / 403 / 404 / 409 |
| 외부 연동 실패 | 502 / 504 |
| 서버 오류 | 500 |

에러의 상태 코드는 `ErrorCode`에 정의돼 있으므로 별도로 지정할 필요가 없습니다.
