# Movi MVP 실행 기준

버전: `v2.0`

갱신일: `2026-09-04`

마감: `2026-08-31`

관련 문서:

- [integration-spec.md](integration-spec.md): 파트별 책임과 제품 정책 — **파트 간 계약의 최우선 기준**
- [ai-api-contract.md](ai-api-contract.md): AI 내부 API와 Mock 계약
- [domain-guide.md](domain-guide.md): 도메인별 불변식과 코딩 주의사항

> 이 문서는 v1.0에서 **일자별 계획을 걷어내고 여전히 유효한 기준만** 남긴 것이다. 지나간
> 날짜의 작업 목록은 git 이력에 있다. 지금 판단에 필요한 것은 "무엇이 되면 끝인가"와
> "무엇을 지켜야 하는가"이지 지난주에 무엇을 했는가가 아니다.

---

## 1. 최종 완료 조건

아래 12개 E2E가 배포 환경에서 통과해야 MVP 완료다.

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

12개 모두 `MviE2eScenarioTest`에 Mock 기반으로 구현돼 통과한다. **남은 것은 실제 외부 연동
환경에서의 검증이다.**

---

## 2. 남은 일

| 항목 | 상태 | 차단 요소 |
|---|---|---|
| AI Voice·FDS 실 연동 | 대기 | staging URL과 계약 정합 — [#104](https://github.com/movi-ai-challenge/movi_backend/issues/104), [movi_ai#1](https://github.com/movi-ai-challenge/movi_ai/issues/1) |
| 오픈뱅킹 Sandbox 종단 검증 | 미검증 | 실 테스트베드 이체 1건 |
| 실제 SMS 발송 | Solapi 연동 완료·도착 미검증 | 실수신 번호로 종단 확인 |
| staging E2E | 미수행 | 배포 서버에 시드 적용 (`movi.seed.enabled=true`) |

배포·시드·E2E는 백엔드 3인 공통 작업이다. **공통 작업도 시작 전에 한 명을 Driver로 지정한다.**
리뷰어와 Driver를 모두 "공통"으로 두지 않는다.

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
| 배포·시드·E2E | 백엔드 3인 | 전원 |

---

## 4. 작업 운영 규칙

- 기능 단위로 브랜치·커밋·PR을 만든다. 서로 다른 기능과 담당자의 파일을 한 PR에 섞지 않는다.
- PR 대상은 `develop`이다. 후속 기능이 앞 PR을 필요로 하면 앞 PR을 먼저 작게 병합한다.
- 기능 PR은 관련 테스트와 `./gradlew build` 통과 후 리뷰를 요청한다.
- **파트 간 계약 변경 PR은 프론트·AI·백엔드 담당자 모두에게 리뷰를 요청한다.**
- 스키마 변경은 `schema.sql`, `ERD.md`, ERDCloud SQL을 함께 수정한다.
- 머지 후 이슈를 직접 닫는다. `develop` 대상 PR은 `Closes #`로 자동 종료되지 않는다.

---

## 5. 기능별 Done 정의

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

## 6. 드랍한 것과 대안

MVP 범위에서 제외했다. 남은 시간에 다시 꺼내지 않는다.

- 거래내역 자연어 기간 고도화
- Google TTS 서버 연동
- SHAP·시각화
- FDS 대안 모델 실험, 카드거래 모델
- 보호자 사전 승인
- 음성 일회용 코드, 거래 바인딩 재인증

계좌 추가 연결·해제와 Voice WebSocket Streaming은 현재 구현 범위에 포함됐다. 기능을
되돌리지 않으며, 계좌 재연결·후속 발화·스트리밍 종료 조건을 회귀 테스트 대상으로 둔다.

**AI staging이 준비되지 않으면** Mock으로 시연하되 화면과 음성에 Sandbox·시연임을 표시한다.
**오픈뱅킹 승인이 늦으면** Mock 이체로 시연하되 같은 표시를 한다.

여기서 Mock은 **백엔드의 Mock 어댑터**(`movi.*.client-type=mock`)를 말한다. AI Mock 서버의
시나리오 강제 헤더(`X-Mock-Scenario`)는 [ai-api-contract.md](ai-api-contract.md) 4절대로
운영 프로파일에서 계속 거부된다. 판정을 헤더로 조작할 수 있으면 시연이 아니라 연출이 된다.

---

## 7. 일일 공유 형식

```text
[기능]
오늘 완료:
내일 계획:
현재 차단 요소:
다른 파트에 필요한 입력:
PR/계약 링크:
검증 결과:
```

"연동 중", "모델 개발 중" 대신 **호출 가능한 URL, JSON, PR, 테스트 결과로** 상태를 표현한다.
