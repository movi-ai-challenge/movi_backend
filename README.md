# Movi Backend

Movi는 시각장애인과 시니어가 화면을 보지 않고도 음성으로 잔액을 확인하고 송금할 수 있도록 돕는 Voice-First 뱅킹 플랫폼입니다.

백엔드는 AI가 분석한 음성 결과를 그대로 실행하지 않습니다. 금액·수취인·계좌 소유권·한도·잔액을 다시 검증하고, 모든 송금에 FDS 평가와 멱등성 검사를 적용합니다.

## 기술 스택

- Java 21
- Spring Boot 4.1.0
- Spring Web MVC / Validation / Security
- Spring Data JPA
- MySQL 8.0
- Gradle 9.5.1
- JUnit 5 / Mockito / H2
- Actuator / Prometheus

## 현재 구현 현황

### 공통 기반

- 모든 API의 성공·실패 응답을 `ApiResponse<T>`로 통일
- 사용자에게 읽어 줄 한국어 `voiceMessage` 제공
- `BusinessException`과 `ErrorCode` 기반 전역 예외 처리
- `@CurrentUser AuthUser` 인증 컨텍스트
- 개발 중 사용할 수 있는 `X-Dev-User-Id` 인증 지원
- JPA 엔티티와 MySQL 스키마 검증
- Actuator health/info/prometheus 엔드포인트 설정

### 잔액 조회

- 기본 계좌 또는 음성 별칭으로 계좌 조회
- 사용자 소유권, 계좌 활성 상태, 오픈뱅킹 연결 만료 검증
- 오픈뱅킹 잔액 조회 Port와 로컬·테스트 Mock Adapter
- 조회 결과를 `BalanceSnapshot`으로 저장
- 금액을 한국어로 변환한 음성 안내
- 송금 확인 직전에 잔액을 다시 조회해 오래된 캐시 사용 방지

> 실제 오픈뱅킹 잔액조회 어댑터는 아직 연결 전이며, `local`과 `test` 프로필에서는 Mock Adapter를 사용합니다.

### 음성 세션과 명령

- 음성 세션 시작 및 사용자 소유권 검증
- WebM/WAV 음성 파일 검증과 최대 5MB 제한
- AI Voice 분석 HTTP Client와 Mock Client
- 음성 분석 응답의 request/session ID, confidence, 필수 필드 검증
- 금액·수취인 누락 시 고정 문구로 재질문
- 이전 발화의 슬롯 저장 및 후속 발화 병합
- 재질문·최종 확인 슬롯 60초 만료
- 같은 슬롯 재질문 최대 3회
- 만료 상태와 슬롯 폐기를 별도 트랜잭션으로 영속화
- 최종 확인과 취소 발화 처리
- 확인 중복 요청 방지를 위한 `PROCESSING` 상태

음성 세션 상태는 다음 방향으로만 전이됩니다.

```text
ACTIVE
├─ CLARIFYING
├─ AWAITING_CONFIRMATION
│  ├─ PROCESSING → COMPLETED
│  └─ CANCELED
└─ EXPIRED
```

> 현재 Mock Voice Client는 기본 `TRANSFER`와 금액 후속 발화만 생성합니다. `CONFIRM`/`CANCEL` 분기는 서비스 테스트와 실제 Voice API 응답을 통해 처리됩니다.

### 송금 검증과 실행

- AI가 추출한 금액·수취인·계좌 별칭 재검증
- STT, intent, 개별 entity confidence 검증
- 등록된 수취인 별칭만 허용
- 직접 계좌번호를 말한 송금 요청 거부
- 기본 계좌 또는 지정 별칭 계좌 선택
- 최소 금액, 1회 한도, 일일 누적 한도 검증
- 확인 직전 실시간 가용 잔액 재검증
- 사용자 행 비관적 잠금으로 동시 일일 한도 우회 방지
- 사용자 잠금 후 `idempotency_key` 조회와 `(user_id, idempotency_key)` DB UNIQUE 이중 방어
- 같은 키의 완료 요청은 기존 결과 반환
- 완료 송금의 출금 거래내역 저장
- 완료 후 수취인 송금 횟수 갱신
- 오픈뱅킹 실행 실패 시 `FAILED` 상태 기록

기본 송금 정책값은 설정으로 관리합니다.

| 정책 | 기본값 |
|---|---:|
| 최소 송금액 | 1원 |
| 1회 한도 | 1,000,000원 |
| 일일 누적 한도 | 3,000,000원 |

송금 상태는 역방향으로 변경할 수 없습니다.

```text
PENDING → RISK_REVIEW → COMPLETED
                      → BLOCKED
        → FAILED / CANCELED
```

> 실제 오픈뱅킹 송금 어댑터는 아직 연결 전입니다. `local`과 `test` 프로필의 `MockTransferExecutionAdapter`는 실제 자금 이동 없이 성공만 모사합니다.

### FDS 이상거래 탐지

- FDS HTTP Client와 로컬·테스트 Mock Client
- 연결 1초, 응답 3초 제한 설정
- request ID, 모델·정책 버전, 점수 범위, risk/decision 조합 검증
- 잔액, 수취인 송금 횟수, 사용자 프로필, 신뢰 기기, STT confidence 전달
- 평가 입력과 정책 버전·점수·사유 코드를 JSON 스냅샷으로 저장
- FDS 호출 또는 응답 검증 실패 시 송금 미실행

| 위험도 | 결정 | 처리 |
|---|---|---|
| LOW | ALLOW | 송금 실행 |
| MEDIUM | ALLOW_WITH_ALERT | 송금 실행 후 보호자 알림 요청 |
| HIGH | BLOCK | 송금 미실행 및 보호자 긴급 알림 요청 |

보호자 알림 실패가 이미 완료되거나 차단된 송금 상태를 되돌리지 않도록 격리되어 있습니다.

> 실제 SMS·알림 저장 어댑터는 아직 연결 전이며, 현재는 Mock Alert Adapter를 사용합니다.

### 보안과 장애 대응

- STT 원문과 AI entity에 포함된 긴 숫자열 마스킹
- 계좌번호·전화번호는 끝 네 자리만 보존
- 응답에 수취 계좌번호 원문 미노출
- DTO 검증 로그에서 rejected value 제거
- 다른 사용자의 세션·계좌·수취인·송금 상태 접근 거부
- FDS 장애 시 Fail-Closed 처리
- 멱등성 키 기반 송금 상태 재조회
- 네트워크 타임아웃 이후 같은 키로 결과 복구 가능

## 공개 API

| Method | Path | 설명 |
|---|---|---|
| `GET` | `/api/accounts/balance` | 기본 또는 별칭 계좌 잔액 조회 |
| `POST` | `/api/voice/sessions` | 음성 세션 시작 |
| `POST` | `/api/voice/sessions/{voiceSessionId}/commands` | 음성 분석, 재질문, 확인·취소 및 송금 실행 |
| `GET` | `/api/transfers/status?idempotencyKey={UUID}` | 타임아웃 이후 송금 상태 조회 |

### 개발 인증

`local` 프로필에서 `movi.auth.dev-mode=true`이면 다음 헤더로 사용자를 지정할 수 있습니다.

```http
X-Dev-User-Id: 3
```

운영 환경에서는 반드시 `movi.auth.dev-mode=false`로 설정해야 합니다.

### 잔액 조회 예시

```bash
curl -H 'X-Dev-User-Id: 3' \
  'http://localhost:8080/api/accounts/balance?accountAlias=생활비%20통장'
```

### 음성 세션 시작 예시

```bash
curl -X POST \
  -H 'X-Dev-User-Id: 3' \
  http://localhost:8080/api/voice/sessions
```

### 음성 명령 예시

```bash
curl -X POST \
  -H 'X-Dev-User-Id: 3' \
  -F 'audio=@voice.webm;type=audio/webm' \
  http://localhost:8080/api/voice/sessions/15/commands
```

최종 확인 발화에는 프론트가 생성한 UUID를 같은 multipart 요청에 추가합니다.

```bash
curl -X POST \
  -H 'X-Dev-User-Id: 3' \
  -F 'audio=@confirm.webm;type=audio/webm' \
  -F 'idempotencyKey=550e8400-e29b-41d4-a716-446655440000' \
  http://localhost:8080/api/voice/sessions/15/commands
```

## 로컬 실행

### 1. 설정 파일 준비

`*.yml`은 인증정보 보호를 위해 Git에서 제외됩니다. 최초 실행 전에 예제 파일을 복사합니다.

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
cp src/test/resources/application-test.yml.example src/test/resources/application-test.yml
```

### 2. MySQL 준비

```bash
mysql -u root -p -e "CREATE DATABASE movi CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
mysql -u root -p movi < docs/schema.sql
```

`src/main/resources/application-local.yml`의 DB 접속 정보를 로컬 환경에 맞게 수정합니다.

### 3. 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

기본 프로필은 `local`이며 Voice, FDS, 잔액조회, 송금, 보호자 알림은 Mock 구현을 사용합니다.

## 빌드와 테스트

```bash
./gradlew build
./gradlew test
./gradlew test --tests "TransferExecutionServiceTest"
```

주요 테스트 시나리오:

- 같은 멱등성 키의 중복·동시 요청
- LOW/MEDIUM/HIGH별 송금 및 알림 분기
- FDS 실패 시 오픈뱅킹 미호출
- 오픈뱅킹 실패 시 `FAILED`
- 일일 한도와 실시간 잔액 부족
- 슬롯 만료와 재질문 횟수 초과
- 바깥 트랜잭션 롤백 후에도 세션 만료 유지
- 다른 사용자 자원 접근 거부
- 계좌번호·전화번호 마스킹
- 완료 후 상태 변경 차단

## 프로젝트 구조

```text
src/main/java/com/movi_backend
├── domain
│   ├── account     # 계좌·잔액조회·오픈뱅킹 연결
│   ├── auth        # 사용자·인증정보·기기
│   ├── transfer    # 송금 검증·실행·거래내역
│   ├── voice       # 음성 세션·AI Voice 연동
│   ├── fds         # FDS 연동·평가 저장
│   └── guardian    # 보호자 관계·알림 엔티티
└── global
    ├── config
    ├── error
    ├── response
    ├── security
    └── util
```

## 외부 연동 상태와 남은 작업

| 항목 | 현재 상태 | 남은 작업 |
|---|---|---|
| AI Voice | HTTP/Mock Client 구현 | 실제 staging API 통합 검증 |
| AI FDS | HTTP/Mock Client 구현 | 모델 승인 버전과 staging 통합 검증 |
| 잔액조회 | Port/Mock 구현 | 실제 오픈뱅킹 Sandbox Adapter |
| 송금 실행 | Port/Mock 구현 | 실제 오픈뱅킹 Sandbox Adapter |
| 보호자 알림 | Port/Mock 구현 | 알림 DB 저장, 실제 SMS Adapter와 재시도 |
| 인증 | 개발 인증 컨텍스트 구현 | JWT 필터·운영 인가 통합 |
| 개인정보 | 음성 텍스트 마스킹 구현 | 전화번호·계좌번호·토큰 AES 계층 최종 연결 |
| 음성 파일 | 형식·5MB 제한 구현 | WebM/WAV 15초 길이 검증 |
| 운영 | Actuator 설정 | Docker/NCP/HTTPS/E2E 배포 검증 |

## 상세 문서

- [도메인 가이드](docs/domain-guide.md)
- [통합 명세](docs/integration-spec.md)
- [AI API 계약](docs/ai-api-contract.md)
- [API 응답 규약](docs/api-response.md)
- [에러 코드](docs/error-codes.md)
- [ERD](docs/ERD.md)
- [MySQL 스키마](docs/schema.sql)
- [백엔드 일정](docs/schedule-backend.md)
- [실행 계획](docs/execution-plan.md)
