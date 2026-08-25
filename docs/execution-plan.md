# Movi MVP 실전 실행계획

버전: `v1.0`

기준일: `2026-08-14`

기간: `2026-08-14 ~ 2026-08-31`

관련 문서:

- [integration-spec.md](integration-spec.md): 파트별 책임과 제품 정책
- [ai-api-contract.md](ai-api-contract.md): AI 내부 API와 Mock 계약
- [schedule-backend.md](schedule-backend.md): 최초 담당 배분과 상위 일정

---

## 1. 최종 완료 조건

아래 12개 E2E가 NCP 배포 환경에서 통과해야 MVP 완료다.

1. 기본 계좌 잔액조회
2. 정상 LOW 음성 송금
3. 금액 누락 후 재질문·보완
4. 수취인 누락 후 재질문·보완
5. 최종 확인 취소
6. 슬롯 또는 확인 세션 만료
7. MEDIUM 이체 완료와 보호자 알림
8. HIGH 이체 미실행과 보호자 알림
9. FDS 장애 시 이체 미실행
10. 같은 멱등성 키의 동시 요청에서 이체 1건
11. 다른 사용자 계좌·세션 접근 거부
12. 로그·응답에 계좌번호·전화번호·토큰 원문 미노출

---

## 2. 작업 운영 규칙

- 기능 단위로 브랜치·커밋·PR을 만든다.
- PR 대상은 `develop`이다.
- 기능 PR은 관련 테스트와 `./gradlew build` 통과 후 리뷰 요청한다.
- 파트 간 계약 변경 PR은 프론트·AI·백엔드 담당자 모두에게 리뷰를 요청한다.
- 스키마 변경은 `schema.sql`, `ERD.md`, ERDCloud SQL을 함께 수정한다.
- 매일 종료 전 공개 API 또는 내부 API 예제를 실제 호출해 본다.
- 일요일 통합 시 처음 합치지 않도록 평일에 작은 PR을 병합한다.

---

## 3. 담당자

| 영역 | 주 담당 | 필수 협업 |
|---|---|---|
| 오픈뱅킹 Port·Mock·계좌·거래내역 | Jun | HANEUL |
| 슬롯 저장·병합·만료 | Jun | HANEUL·AI |
| 인증·JWT | jjh | 프론트 |
| 보호자·SMS·알림 기록 | jjh | HANEUL |
| 잔액조회 | HANEUL | Jun |
| 이체 검증·확인·실행·멱등성 | HANEUL | Jun·jjh |
| FDS Client·결과 적용 | HANEUL | AI |
| STT·Intent·Entity·Voice Mock | AI | 프론트·백엔드 |
| FDS 모델·룰·Mock | AI | HANEUL |
| 음성 녹음·화면 상태·기기 TTS | 프론트 | AI·백엔드 |
| NCP·Docker·시드·E2E | 백엔드 3인 | 전원 |

공통 작업도 작업 시작 전에 한 명을 Driver로 지정한다. 리뷰어와 Driver를 모두 “공통”으로 두지 않는다.

---

## 4. 현재 기준선과 간극

### 준비됨

- 공통 `ApiResponse`, `PageResponse`
- 에러 코드와 TTS 문구 구조
- `@CurrentUser` 인증 컨텍스트
- 20개 엔티티와 17개 enum
- 이체 상태머신
- 이체 상태 테스트 PR
- Transfer/FDS Repository PR

### 미완료

- OpenBanking Port·Mock
- 계좌 Repository와 잔액조회 API
- AI Voice Client·DTO
- 세션 슬롯 컬럼과 상태 enum
- `CONFIRM`, `CANCEL` Intent
- 이체 명령 검증·확인
- Mock/실 FDS Client
- LOW/MEDIUM/HIGH 애플리케이션 흐름
- 알림 연결
- 운영 프로파일·Docker·NCP 배포

---

## 5. 일자별 계획

### 8/14 — 계약 고정

#### 백엔드

- 이 문서 세트 PR 생성
- 기존 상태 테스트·Repository PR 리뷰와 병합 요청
- 현재 코드와 계약 간 변경 목록을 이슈 또는 작업표에 등록

#### AI

- Voice/FDS 계약 리뷰
- 학습 피처와 운영 피처 매핑표 제출
- OpenAPI 또는 예제 JSON 제출

#### 프론트

- WebM/Opus 녹음 가능 여부 확인
- `multipart/form-data` 업로드 방식 확인
- 화면 상태와 보관값 리뷰

#### Done

- [ ] 세 파트가 계약 문서에 리뷰 의견을 남김
- [ ] enum·JSON·timeout·Mock 기한에 이견이 없거나 변경 PR이 있음
- [ ] 정상·누락·LOW/MEDIUM/HIGH 예제 JSON이 존재함

### 8/15 — Voice 계약 구현

#### AI

- 실행 가능한 Voice Mock 제공
- 정상 송금, 누락, 확인, 취소, 낮은 신뢰도 응답 제공

#### 백엔드

- `VoiceIntent.CONFIRM/CANCEL`
- Voice 요청·응답 DTO
- `VoiceAnalysisClient` 인터페이스와 Mock/HTTP 경계
- Intent·confidence·entity 스키마 검증

#### 프론트

- 녹음 권한·시작·종료
- 15초·5MB 제한
- 업로드·분석 상태 UI

#### 테스트

- 정상 `TRANSFER`
- 금액/수취인 누락
- 낮은 STT/NLU confidence
- 알 수 없는 Intent 거부

### 8/16 — 첫 통합 체크포인트

#### 백엔드

- `POST /api/voice/sessions`
- Voice Mock 호출
- 세션 소유권 검증
- OpenBanking Mock 기반 계좌 목록·잔액조회 골격

#### 통합 시나리오

```text
프론트 녹음
→ 백엔드 업로드
→ AI Voice Mock
→ TRANSFER Entity 수신
→ 백엔드 검증
→ 재질문 또는 확인 응답
```

별도로 `로그인 컨텍스트 → 계좌 목록 → Mock 잔액`이 동작해야 한다.

### 8/17 — 잔액조회 완성·FDS Mock 제공

#### Jun

- OpenBanking Port/Mock
- 계좌 저장·조회 기반

#### HANEUL

- 기본/지정 계좌 잔액조회
- 계좌 소유권·활성·연결 만료 검증
- `BalanceSnapshot` 저장
- 한국어 금액 안내

#### AI

- 실행 가능한 FDS Mock 제공
- 피처 매핑표 확정

### 8/18 — 민감정보·Voice staging

- 계좌번호 마스킹·로그 검증
- AI Voice staging 연결
- Voice 10초 timeout
- STT 장애를 `VOICE_5000`으로 변환
- 음성 원본 삭제 정책 검증

### 8/19 — 수취인 조회

- 사용자 ID+별칭 정확 일치 조회
- 존재하지 않는 수취인 처리
- 다른 사용자 수취인 접근 거부
- 수취계좌 마스킹
- 직접 계좌번호 음성 송금 거부
- Fuzzy 결과는 자동 선택하지 않고 재질문

### 8/20 — 세션·슬롯·스키마

- `voice_sessions` 상태·pending slots·만료·retry 저장
- 엔티티·DDL·ERD 동시 수정
- 새 Entity와 기존 슬롯 병합
- 60초 만료
- 3회 재질문 제한

필수 테스트:

- 금액 누락 후 금액만 보완
- 수취인 누락 후 수취인만 보완
- 만료된 슬롯 미사용
- 다른 사용자 세션 접근 거부

### 8/21 — 확인·취소

- 검증된 이체 확인 DTO
- 확인 문장과 `confirmationId`
- `AWAITING_CONFIRMATION`
- CONFIRM/CANCEL 적용
- 60초 만료
- 정보 변경 시 기존 확인 폐기
- 프론트 `idempotencyKey` 생성·재사용

### 8/22 — LOW 이체 완주

```text
CONFIRM
→ Transfer PENDING
→ RISK_REVIEW
→ Mock FDS LOW/ALLOW
→ FdsAssessment 저장
→ Mock OpenBanking 이체
→ COMPLETED
→ Transaction 저장
→ recipient.transferCount 증가
→ 완료 음성 안내
```

필수 테스트:

- FDS 없이 완료 불가
- LOW 이체 완료
- 오픈뱅킹 실패 시 FAILED
- 완료 후 상태 변경 불가

### 8/23 — 통합 체크포인트 2

- 실제 프론트 녹음에서 Mock 이체 완료
- 금액 누락 재질문
- 확인 취소
- 동일 키 재요청
- 영상 또는 요청·응답 로그로 결과 공유
- 실제 FDS staging API 인수

### 8/24 — 실제 FDS 연결·NCP 준비

- `FdsClient` HTTP 구현
- connect 1초/response 3초
- 응답 검증기
- `modelVersion`, `policyVersion`, score, reason 스냅샷 저장
- AI 모델 승인자료 리뷰
- NCP Ubuntu, 방화벽, SSH 사용자 준비

### 8/25 — MEDIUM/HIGH·알림

- MEDIUM 이체 완료 후 알림 요청
- HIGH 오픈뱅킹 미호출·BLOCKED
- HIGH 긴급 알림
- 알림 실패가 완료/차단 상태를 역전하지 않음
- 잘못된 risk/decision 조합 거부

### 8/26 — 최초 NCP 배포

- Dockerfile
- Compose: backend+mysql+nginx
- Java 21·MySQL 8
- 운영 환경변수와 `dev-mode=false`
- `/actuator/health` 또는 동등한 헬스체크
- MySQL 3306 비공개
- 최초 staging 배포

### 8/27 — 멱등성·동시성

- 같은 키 사전 조회
- DB UNIQUE 최종 방어
- 동시에 같은 키 요청
- 외부 이체 1회 보장
- 프론트 timeout 재시도에서 같은 키 사용

### 8/28 — 장애·보안·배포 안정화

- AI Voice/FDS timeout
- OpenBanking timeout
- 잘못된 AI JSON
- SMS 실패
- 로그 마스킹
- HTTPS
- MySQL dump 백업·복구 시험
- GitHub Actions 또는 수동 배포 체크리스트 고정

### 8/29 — 최종 배포·시드

- 최신 `develop` 배포
- 테스트 사용자·계좌·수취인
- LOW/MEDIUM/HIGH·cold-start 시나리오
- NCP 비용 알림과 무료 종료일 기록
- 배포 태그 생성

### 8/30 — E2E

- 1절의 12개 시나리오 실행
- 각 실패의 “돈이 빠져나가지 않음” 확인
- 프론트 음성 안내 확인
- 시연 순서와 예비 시나리오 확정

### 8/31 — 버그 버퍼

- 신규 기능 금지
- E2E 결함만 수정
- 발표 환경 재기동·복구 확인

---

## 6. 기능별 PR 분리

권장 브랜치·커밋 단위:

```text
docs/integration-spec
feat/voice-ai-contract
feat/voice-session-slots
feat/balance-inquiry
feat/transfer-validation
feat/transfer-confirmation
feat/fds-client
feat/transfer-risk-flow
feat/transfer-idempotency
chore/ncp-deployment
```

서로 다른 기능과 담당자의 파일을 한 PR에 섞지 않는다. 후속 기능이 앞 PR을 필요로 하면 앞 PR을 먼저 작게 병합한다.

---

## 7. 기능별 Done 정의

기능 하나는 다음을 모두 만족해야 완료다.

1. 요청·응답과 정책이 문서화됨
2. 정상 흐름이 동작함
3. 필수 오류 흐름이 동작함
4. 사용자 소유권과 민감정보를 검증함
5. 핵심 단위 또는 통합 테스트가 있음
6. `./gradlew build` 통과
7. 기능 단위 커밋과 `develop` 대상 PR
8. 소비 파트가 staging 또는 Mock에서 실제 호출
9. 새 설정이 있으면 팀 채널(Notion/카톡)에도 공유
10. 스키마 변경이면 DDL·ERD가 일치

---

## 8. 중단·드랍 기준

8/23까지 LOW 음성 이체가 완주하지 못하면 다음을 즉시 드랍한다.

- 거래내역 자연어 기간 고도화
- Google TTS 서버 연동
- SHAP·시각화
- 알림 재시도
- FDS 대안 모델 실험
- 카드거래 모델

8/26 최초 배포가 실패하면 GitHub Actions 자동화보다 수동 체크리스트 배포를 우선한다. 실제 오픈뱅킹 승인이 늦으면 Mock 이체로 시연하되 화면과 음성에 Sandbox/시연임을 표시한다.

---

## 9. 일일 공유 형식

각 담당자는 매일 다음 형식으로 공유한다.

```text
[기능]
오늘 완료:
내일 계획:
현재 차단 요소:
다른 파트에 필요한 입력:
PR/계약 링크:
검증 결과:
```

“연동 중”, “모델 개발 중” 대신 호출 가능한 URL, JSON, PR, 테스트 결과로 상태를 표현한다.
