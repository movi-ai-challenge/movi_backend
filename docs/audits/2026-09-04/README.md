# MOVI 사용자 흐름 점검 — 2026-09-04

백엔드 `2515d58`, 프런트 `bdf01a4`, AI `2e1b9a7c` 및 점검 시점의 작업 디렉터리 기준이다. 수취인 검증은 `fix/128-verified-transfer-target`에서 별도로 수정 중이므로 수취인 검증/자동 등록 관련 기존 지적은 이번 발견 목록에서 제외했다. 점검 과정에서는 앱 소스와 DB를 변경하지 않았다.

검증 범위는 세 저장소의 코드 경로 연결과 오프라인 재현이다. 실제 은행·SMS·STT를 호출하거나 실계좌 송금을 수행하지 않았다. 실기기 스크린리더·브라우저 E2E까지 완료한 보고서는 아니다. 아래의 ‘코드 확인’은 해당 조건에서 실행되는 코드를 확인했다는 뜻이다.

P0는 다른 수정에 앞서 막아야 하는 승인 오류, P1은 금융 결과 오안내·중복 요청 위험·핵심 흐름 단절, P2는 기능 진입 및 접근성/사용성 문제다.

## 1. [P0] 질문·다시 듣기 요청을 송금 승인으로 처리한다

- 재현: 송금 확인 질문을 받은 상태에서 `어느 은행이야?`, `다시 읽어줘`, `얼마 보내는 거야?`, `잠깐 확인해볼게`를 말한다. AI가 `UNKNOWN`으로 응답해도 백엔드가 `CONFIRM`으로 바꾼다.
- 원인: 승인 후보에 `어`, `네`, `보내`, `확인` 등이 있고 전체 문장에 `contains()`로 검사한다. AI가 먼저 `CONFIRM`으로 분류하면 실제 발화의 부정 표현도 확인하지 않고 승인한다. 확인 실행 경로에는 최초 송금 명령처럼 0.80 이상이라는 신뢰도 검사가 없다. 응답 validator는 0~1 범위만 확인한다.
- 영향: 사용자는 내용을 재확인하거나 다시 들으려 했는데 송금 실행/FDS 단계로 넘어갈 수 있다. FDS는 사용자가 동의했는지를 대신 검증하지 않는다.
- 수정: 최종 승인 표현은 전체 발화 의미가 명확한 경우에만 수용한다. 부정·정정·질문·다시 듣기·모순된 응답은 승인하지 않는다. 신뢰도가 낮거나 intent와 발화가 모순되면 재질문한다. `다시 듣기`는 저장된 확인 내용을 재생하는 동작이어야 한다.
- 검증: 실제 Java 판정 메서드와 상수를 추출해 실행했다. 네 가지 질문 모두 `CONFIRM`, `intent=CONFIRM / 발화=아니요 취소할게요`도 `CONFIRM`이었다. 전체 이체는 실행하지 않았다.
- 근거: [승인 단어](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L75), [판정](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L479), [확인 실행](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L550).

## 2. [P1] 은행 응답 유실을 ‘실패·돈 안 나감’으로 확정한다

- 재현 조건: 은행은 이체를 처리했지만 백엔드에 응답이 도착하지 않거나, 이체 호출 중 통신 예외가 난다.
- 원인: `executeTransfer()`가 모든 `RuntimeException`을 `transfer.fail()`로 바꾼다. 프런트는 `FAILED`에 대해 `이 송금으로 돈이 나가지 않았습니다`라고 표시하고 복구 키를 지운다.
- 영향: 실제 출금 여부가 불확실한 상황에서 사용자가 새 송금을 시작할 수 있다. 같은 키 재조회도 잘못 확정한 FAILED를 반환하므로 현재 복구 기능만으로 해결되지 않는다.
- 수정: 은행의 명확한 거절과 응답 미확인을 구분한다. 미확인은 상태 조회·대사로 확정할 때까지 유지하고 새 이체로 재시도하라고 안내하지 않는다. 은행 거래 식별자와 내부 멱등키 연결도 보존한다.
- 검증: 예외 처리부터 화면 문구까지 코드 확인. 실제 은행 응답 유실은 미실험.
- 근거: [일괄 실패 처리](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/transfer/application/TransferExecutionService.java#L461), [돈이 나가지 않았다는 표시](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/result/page.tsx#L252).

## 3. [P1] 새로고침 후 송금 결과를 복구하는 코드가 실제 화면에서 쓰이지 않는다

- 재현: 송금 요청을 보낸 뒤 응답이 오기 전에 새로고침하거나, 결과 확인에 실패한 상태에서 홈으로 돌아온다.
- 원인: 복구 키는 sessionStorage에 저장하지만 이를 읽는 호출은 `VoiceCommandPanel` 하나뿐이다. 이 컴포넌트는 현재 어떤 화면에도 렌더되지 않는다. 실제 홈과 결과 페이지는 저장 키를 읽지 않으며, 결과 state가 없으면 새 송금 링크를 보여준다.
- 영향: 결과 불명 상태를 사용자에게 복구해 주지 못하고, 새 송금을 시작하면 이전 키를 덮어쓸 수 있다. 테스트가 서비스 함수만 통과해도 실제 제품 흐름은 작동하지 않는 사례다.
- 수정: 실제 앱 진입점에서 사용자별 미확정 이체를 복원하고 결과 확정 전에는 해당 송금을 새 요청으로 만들지 않게 한다. 종료 결과는 거래내역/결과 재조회로 연결한다.
- 검증: `readTransferRecoveryKey` 및 `VoiceCommandPanel`의 모든 참조를 검색해 확인.
- 근거: [유일한 복구 호출](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/components/domain/voice/VoiceCommandPanel.tsx#L148), [결과 없음 화면](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/result/page.tsx#L111), [저장소](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/services/transferRecoveryStorage.ts#L49).

## 4. [P1] 송금 실행 중에도 ‘취소하고 정보 수정하기’로 새 송금을 시작할 수 있다

- 재현: 직접 송금 실행 → 느린 응답 중 ‘취소하고 정보 수정하기’ → 다시 같은 사람과 금액으로 검토·실행.
- 원인: 실행 중에도 이동 링크가 활성화돼 있다. 입력 페이지 진입 시 확인 데이터와 결과를 지우며, store의 `clearDirectTransferReview()`가 요청 잠금까지 푼다. 새 검토는 새 confirmationId와 새 멱등키를 만든다.
- 영향: 첫 송금은 취소되지 않았는데 사용자는 취소했다고 생각할 수 있다. 새 확인/키이므로 백엔드의 같은 키 중복 방어가 두 번째 송금을 막지 못한다.
- 수정: 검토 단계 취소와 실행 이후 상태 확인을 분리한다. 실행 후에는 ‘취소’라고 표시하지 말고 진행 상태를 유지한다. 화면 이동/새로고침에도 미확정 요청을 보존한다.
- 검증: 라우팅·store 초기화·키 발급 경로 확인. 실제 이체 두 건은 실행하지 않았다.
- 근거: [실행 중에도 남는 링크](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/review/page.tsx#L121), [입력 페이지 초기화](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/page.tsx#L48), [잠금 해제](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/store/useBankStore.ts#L85).

## 5. [P1] 확인 녹음 중 화면을 벗어날 때 오디오가 전송될 수 있다

- 재현 조건: 음성 확인 녹음을 시작한 뒤 다른 화면으로 이동한다. 트랙 종료로 MediaRecorder의 stop 이벤트가 발생한다.
- 원인: unmount cleanup은 마이크 트랙만 중지하며 `onstop`을 제거하지 않는다. `onstop`은 녹음 종료 사유와 무관하게 `send(recorded)`를 호출한다.
- 영향: 화면을 벗어나 녹음을 그만둔 행동이 확인 발화 제출이 될 수 있다. 일부만 녹음된 긍정 발화와 1번 판정 오류가 결합되면 특히 위험하다.
- 수정: 취소/unmount 플래그와 이벤트 해제로 녹음 폐기와 정상 제출을 구분한다. getUserMedia 대기 중 화면을 벗어나는 경우도 후속 녹음을 시작하지 않게 한다.
- 검증: 실제 훅 코드를 실행하고 React·마이크·HTTP만 대역으로 바꾼 이벤트 모의에서 unmount 후 전송 1회를 확인했다. 실브라우저 이벤트 순서는 별도 재현 필요.
- 근거: [cleanup](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/hooks/useConfirmationRecorder.ts#L106), [stop 이벤트 전송](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/hooks/useConfirmationRecorder.ts#L240).

## 6. [P1] 음성 확인 응답이 다시 재질문/확인 대기이면 대화 연결을 잃는다

- 재현: 확인 중 사용자가 내용을 바꿔 말해 서버가 새 `AWAITING_CONFIRMATION` 또는 `CLARIFYING`을 돌려준다.
- 원인: 훅은 정상 HTTP 응답을 모두 `onSettled()`로 전달한다. 홈의 `onSettled`는 상태와 무관하게 pendingConfirmation과 voiceSessionId를 null로 만든다.
- 영향: 새 확인 문장은 들리지만 이어서 대답할 ID가 없어 ‘이체 화면에서 확인’으로 빠지거나, 새 세션에서 다시 시작하게 된다.
- 수정: 금융 완료/차단/실패/취소와 대화 중간 상태를 구분한다. 중간 상태에서는 최신 confirmationId와 기존 세션을 유지한다.
- 검증: 실제 훅에 AWAITING_CONFIRMATION 응답을 주입해 onSettled로 전달됨을 확인했고, 홈의 무조건 초기화를 확인했다.
- 근거: [모든 정상 응답 전달](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/hooks/useConfirmationRecorder.ts#L160), [세션 초기화](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/page.tsx#L320).

## 7. [P1] 재질문에 ‘오만 원’만 답하면 처리되지 않는다

- 재현: `모비야 엄마에게 보내줘` → `얼마를 보내시겠어요?` → 마이크를 눌러 `오만 원`.
- 원인: 프런트는 같은 백엔드 세션을 유지하지만, AI의 새 WebSocket마다 StreamSession이 activated=false로 시작한다. expectedSlots가 있어도 호출어를 들은 final만 분석한다.
- 영향: 정상적인 대화 답변이 무시된다. 사용자는 매번 `모비야 오만 원`이라고 해야 하는데, 홈은 ‘이어서 말씀해 주세요’라고 안내한다.
- 수정: 백엔드가 검증한 후속 대화 문맥이 있는 연결은 호출어 없이 답변을 받는다. 최초 상시 대기와 사용자 조작 후의 후속 답변을 구분한다.
- 검증: 실제 StreamSession에 final 결과를 주입했다. `오만 원`은 activated=false/command 빈값, `모비야 오만 원`은 true였다.
- 근거: [새 세션](https://github.com/movi-ai-challenge/movi_ai/blob/2e1b9a7c/src/voice_analysis/api.py#L805), [호출어 필터](https://github.com/movi-ai-challenge/movi_ai/blob/2e1b9a7c/src/voice_analysis/api.py#L842), [초기 상태](https://github.com/movi-ai-challenge/movi_ai/blob/2e1b9a7c/src/voice_analysis/stream_session.py#L33).

## 8. [P1] 금액/수취인 재질문 중에는 ‘취소’나 다른 작업으로 전환하기 어렵다

- 재현: 금액을 묻는 상태에서 `모비야 취소해줘` 또는 `모비야 잔액부터 알려줘`.
- 원인: 후속 분석은 요청받은 필드 하나만 추출하고 intent를 기존 expectedIntent=TRANSFER로 고정한다. 금액 파서가 실패하면 TRANSFER/0.3이므로 취소가 아니라 신뢰도 오류가 된다. 백엔드도 CANCEL 처리는 확인 대기 분기에만 있고 일반/재질문 상태에서는 TRANSFER가 아니면 거절한다.
- 영향: 사용자의 중단 의사가 반영되지 않고 기존 대화에 갇힌다. 후속 발화에 정정한 다른 정보가 함께 있어도 하나의 슬롯만 남길 수 있다.
- 수정: 모든 대화 상태에서 취소·작업 전환·정정을 슬롯 추출보다 먼저 처리한다. 취소한 세션의 이전 값을 다음 송금에 병합하지 않는다.
- 검증: 실제 `_analyze_follow_up` 함수에 금액 추출 실패 대역과 취소 발화를 넣어 TRANSFER/0.3이 유지됨을 확인했다. LLM 실응답은 호출하지 않았다.
- 근거: [intent 고정](https://github.com/movi-ai-challenge/movi_ai/blob/2e1b9a7c/src/voice_analysis/api.py#L666), [백엔드 분기](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L197), [일반 intent 제한](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L237).

## 9. [P1] 은행을 지정해 조회해도 기본 계좌 내역/잔액을 답할 수 있다

- 재현: 기본 계좌가 국민은행이고 다른 신한은행 계좌도 있을 때 `신한은행 거래내역 알려줘` 또는 `신한은행 잔액 알려줘`.
- 원인: 거래내역은 `findSourceAccount(userId, null)`로 고정한다. 잔액은 sourceAccountAlias만 사용한다. AI가 조회 은행을 `bank`로 추출하면 계약 mapper는 bankName에 넣으므로 잔액 조회에서도 무시된다. 거래내역은 alias를 받아도 무시한다.
- 영향: 다른 계좌의 수치를 사용자에게 답한다. 응답에 계좌명이 들어 있어도 ‘왜 내 요청과 다르지?’를 사용자가 알아차리고 고쳐야 한다.
- 수정: 조회에서도 은행/별칭/계좌 선택 조건을 적용한다. 같은 은행에 여러 계좌면 되묻고, 사용자가 지정한 계좌를 찾지 못했으면 기본 계좌로 조용히 대체하지 않는다.
- 검증: AI 필드 매핑과 백엔드 소비 경로를 대조했다.
- 근거: [거래내역 기본 계좌 강제](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L434), [잔액 계좌 선택](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/voice/application/VoiceCommandService.java#L403), [AI 필드 매핑](https://github.com/movi-ai-challenge/movi_ai/blob/2e1b9a7c/src/voice_analysis/contract_mapper.py#L156).

## 10. [P1] 연결 해제한 계좌를 다시 연결해도 돌아오지 않는다

- 재현: 연결 계좌 해제 → 같은 은행 인증으로 재연결 → 은행이 이전과 동일한 fintechUseNum을 반환.
- 원인: 해제는 active=false로 남긴다. 재연결은 해당 fintechUseNum 행이 존재하면 상태와 무관하게 건너뛴다. 재활성화 경로가 없다.
- 영향: 실수로 해제한 사용자가 되돌릴 수 없다. 마지막 계좌였다면 잔액/송금이 막힌다. 거래내역 목록도 inactive 계좌를 거절하므로 예전 이력을 화면에서 찾기 어렵다.
- 수정: 소유권이 검증된 기존 계좌는 재연결 시 활성화하고 연결 정보를 갱신한다. 기존 거래 이력과 기본 계좌 정책을 유지한다. 해제 계좌의 과거 내역 접근 정책도 분리한다.
- 검증: 해제·재등록·조회 코드 확인. 실제 은행 재연결 미실험.
- 근거: [해제](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/account/application/AccountService.java#L120), [재등록 건너뛰기](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/account/application/OpenBankingConnectService.java#L127), [과거 목록 조회 거절](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/transfer/application/TransactionQueryService.java#L53).

## 11. [P2] 5분이 지나도 잠긴 로그인 버튼이 풀리지 않는다

- 재현: PIN 또는 비밀번호 5회 오류 → 잠금 안내 → 같은 화면에서 5분 기다림 → 올바른 정보를 입력.
- 원인: 서버는 lockedUntil 경과 후 인증을 허용하지만 프런트는 isLocked=true를 되돌릴 코드가 없다. 다른 아이디/번호를 입력해도 버튼이 잠긴다.
- 영향: 안내대로 기다린 사용자가 계속 로그인할 수 없다. 화면을 나갔다 들어와야 한다.
- 수정: 서버 잠금 종료 시각을 안내하고 이후 재시도를 허용한다. 클라이언트의 버튼 활성화가 서버 잠금 검사를 대신하지 않도록 한다.
- 근거: [PIN 잠금](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/login/pin/page.tsx#L106), [비밀번호 잠금](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/login/password/page.tsx#L93).

## 12. [P2] 일반 사용자가 보호자를 등록하는 프런트 흐름이 없다

- 재현: 시드 계정이 아닌 신규 가입자가 보호자 알림을 사용하려고 설정/홈을 찾는다.
- 원인: 백엔드 POST /api/v1/guardian-links는 있지만 프런트에 이를 호출하는 서비스/화면이 없다. 회원가입의 phoneNumber는 본인의 번호이며 보호자 링크를 만들지 않는다. 그런데 가입 도움말은 번호를 적으면 보호자에게 문자를 보낼 수 있다고 설명한다.
- 영향: 시드로 미리 연결된 시연 계정에서는 보이던 알림이 신규 사용자에게는 나가지 않는다. 서버는 ACTIVE 링크가 없으면 알림을 생성하지 않는다.
- 수정: 보호자 금융 대시보드와 ‘내 보호자 연락처 등록’을 구분한다. 최소 등록/조회/연결 상태 안내를 제공하고 미등록 상태를 숨기지 않는다. SMS 발송 연동과 실제 도착 확인은 별도로 검증한다.
- 추가 사용성: SMS는 ‘앱에서 확인’이라고 하지만 보호자는 비회원일 수 있고 문자에 확인 링크도 없다. 등록 정책에 맞게 수신자가 실제로 할 수 있는 다음 행동을 안내해야 한다.
- 검증: 프런트 src 전체 참조 검색 및 서버 알림 대상 조회 확인. Solapi 구현은 존재하므로 ‘SMS 구현 자체가 없다’는 지적은 하지 않는다.
- 근거: [가입 도움말](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/signup/page.tsx#L188), [보호자 등록 API](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/guardian/controller/GuardianLinkController.java#L27), [발송 대상](https://github.com/movi-ai-challenge/movi_backend/blob/2515d58/src/main/java/com/movi_backend/domain/guardian/application/GuardianNotificationTransactionService.java#L45).

## 13. [P2] ‘글씨 크게 보기’를 켜도 핵심 안내와 설정 글씨가 그대로다

- 재현: 설정에서 큰 글씨 켜기 → 설정 설명, 홈의 마이크 상태, 빠른 메뉴/보조 안내 비교.
- 원인: 모드는 root font-size를 125%로 바꾸지만 해당 글씨는 text-[13px], text-[15px] 등 고정 px다. rem 글씨만 커져 화면 크기가 제각각이 된다.
- 영향: 저시력 사용자가 가장 먼저 읽어야 할 ‘지금 말해도 되는지’와 설정 설명이 충분히 확대되지 않는다.
- 수정: 핵심 글씨 크기를 rem/공통 토큰으로 통일하고 125% 설정 및 200% 브라우저 확대에서 실제 레이아웃을 확인한다.
- 추가: 기본 muted 색 #6b7ba4는 surface #0c1228 위에서 계산 대비 4.409:1이다. 작은 일반 설명 글씨의 4.5:1 목표에 조금 못 미친다. 배경 #05091a 위에서는 4.710:1이므로 바탕색별로 검사해야 한다.
- 검증: CSS 단위 확인과 대비 공식 계산. 실제 확대 화면/스크린리더 검증은 미수행.
- 근거: [큰 글씨 설정](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/globals.css#L110), [설정의 고정 px](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/components/common/AccessibilitySettingsPanel.tsx#L78), [홈 상태 안내](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/page.tsx#L545).

## 기능 오류와 구분할 사용성 개선 후보

- 직접 송금 화면에 이번 송금의 출금 계좌 선택이 없다. defaultAccountId만 보내므로 다른 통장에서 한 번 보내려면 전역 기본 계좌를 먼저 바꿔야 한다. ‘이번 송금의 출금 계좌’ 선택과 기본 계좌 변경을 분리하는 편이 자연스럽다. [입력 처리](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/page.tsx#L85)
- 수취인 목록은 은행 이름 대신 ‘은행 코드 088’처럼 표시한다. 화면과 낭독에서 은행명으로 안내해야 사용자가 알아볼 수 있다. [목록](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/app/transfer/page.tsx#L142)
- 음성 확인 UI에는 명시적 ‘확인/취소/다시 듣기’ 조작 대신 ‘음성으로 대답하기’만 있다. 마이크 장애나 조용한 환경에서는 기존 확인 내용을 보존한 채 키보드/터치로 마무리할 수 있는 대체 동작이 필요하다. [확인 UI](https://github.com/movi-ai-challenge/movi_frontend/blob/bdf01a4/src/components/domain/voice/VoiceConfirmation.tsx#L24)
- 직접 송금 결과 화면은 voiceMessage 텍스트를 표시하지만 기기 TTS 재생/다시 듣기 동작이 없다. 화면에 존재하는 텍스트와 음성 안내 기능은 별개다. 스크린리더 낭독 결과는 실기기에서 확인해야 한다.
- 문서는 현재 구현보다 뒤처져 있다. integration-spec의 비목표에 새 계좌 음성 송금/계좌 해제가 남아 있고 execution-plan에는 이미 존재하는 SMS/스트리밍 구현이 미연동·제외로 적혀 있다. 팀원이 문서를 보고 수정하다 기능을 되돌리지 않도록 이번 확정 범위로 갱신해야 한다.
- 현재 직접 송금의 별도 PIN 재인증 부재는 integration-spec에 명시된 MVP 정책이다. 이를 ‘구현 누락’으로 단정하지 않았다. 실제 서비스에서 요구하는 인증 수준은 제품 결정으로 별도 확정해야 한다.

## 권장 처리 순서 및 완료 조건

1. 1번 승인 오류를 가장 먼저 수정한다. 질문·정정·취소·다시 듣기·낮은 신뢰도의 어떤 조합도 이체 실행으로 가지 않는 테스트를 추가한다.
2. 2~6번을 ‘미확정 송금 복구와 확인 상태 유지’ 묶음으로 해결한다. 응답 유실·새로고침·뒤로 가기·화면 이탈·다중 클릭에서도 승인 없이 실행되지 않고, 동일 의도의 이체가 새 키로 중복되지 않는지 검증한다.
3. 7~10번은 음성 다중 턴과 계좌 선택/복구의 종단 시나리오로 검증한다. 실제 AI와 백엔드 사이 필드 매핑을 포함해야 한다.
4. 11~13번 및 대체 조작은 신규 사용자 계정으로 점검한다. 시드로 사전 세팅된 계정만으로 완료 판단하지 않는다.

오프라인 재현 결과: [Java/AI/대비 결과](probe-results.json), [확인 녹음 이벤트 결과](confirmation-lifecycle-results.json).
재현 결과 파일은 금융 API를 호출하지 않은 오프라인 점검 결과다. Java 판정은 원본 메서드·상수를 추출해 실행했고, AI 스트림은 원본 클래스를 호출했다. 녹음 lifecycle은 원본 훅에 React·마이크·HTTP 대역을 주입한 모의 결과이므로 실브라우저 검증을 대체하지 않는다.
