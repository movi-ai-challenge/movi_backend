-- =====================================================
-- Movi MVP — ERDCloud 임포트용 DDL
-- 사용법: ERDCloud → 우측 상단 [Import] → [DDL] → 전체 붙여넣기
-- =====================================================

CREATE TABLE users (
    user_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 ID',
    name       VARCHAR(50)  NOT NULL COMMENT '이름',
    phone      VARCHAR(255) NOT NULL COMMENT '전화번호(AES 암호화)',
    phone_hash VARCHAR(64)  NULL COMMENT '전화번호 중복 확인용 HMAC-SHA256',
    birth_date DATE         NULL COMMENT '생년월일',
    user_type  VARCHAR(30)  NOT NULL COMMENT 'SENIOR/VISUALLY_IMPAIRED/GENERAL',
    status     VARCHAR(20)  NOT NULL COMMENT 'ACTIVE/DORMANT/WITHDRAWN',
    token_version BIGINT    NOT NULL DEFAULT 0 COMMENT '로그아웃 시 JWT 일괄 무효화 버전',
    created_at DATETIME     NOT NULL COMMENT '생성일시',
    updated_at DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_phone_hash (phone_hash)
) COMMENT '사용자';

CREATE TABLE oauth_accounts (
    oauth_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'OAuth ID',
    user_id          BIGINT       NOT NULL COMMENT '사용자 ID',
    provider         VARCHAR(20)  NOT NULL COMMENT 'KAKAO',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '제공자 사용자 식별자',
    access_token     VARCHAR(512) NULL COMMENT '액세스 토큰(암호화)',
    refresh_token    VARCHAR(512) NULL COMMENT '리프레시 토큰(암호화)',
    token_expires_at DATETIME     NULL COMMENT '토큰 만료일시',
    created_at       DATETIME     NOT NULL COMMENT '생성일시',
    updated_at       DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (oauth_id),
    UNIQUE KEY uk_oauth_provider_user (provider, provider_user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '소셜 로그인 계정';

CREATE TABLE user_credentials (
    credential_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '인증정보 ID',
    user_id           BIGINT       NOT NULL COMMENT '사용자 ID',
    pin_hash          VARCHAR(255) NOT NULL COMMENT 'PIN 해시(BCrypt)',
    biometric_enabled TINYINT      NOT NULL COMMENT '생체인증 사용 여부',
    failed_attempts   INT          NOT NULL COMMENT '연속 실패 횟수',
    locked_until      DATETIME     NULL COMMENT '잠금 해제 시각',
    pin_updated_at    DATETIME     NOT NULL COMMENT 'PIN 변경일시',
    PRIMARY KEY (credential_id),
    UNIQUE KEY uk_credential_user (user_id),
    CONSTRAINT fk_credential_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT 'PIN/생체 인증정보';

CREATE TABLE devices (
    device_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '기기 ID',
    user_id       BIGINT       NOT NULL COMMENT '사용자 ID',
    device_uuid   VARCHAR(100) NOT NULL COMMENT '기기 고유값',
    device_model  VARCHAR(100) NULL COMMENT '기기 모델명',
    os_version    VARCHAR(50)  NULL COMMENT 'OS 버전',
    push_token    VARCHAR(512) NULL COMMENT '푸시 토큰',
    is_trusted    TINYINT      NOT NULL COMMENT '신뢰 기기 여부(FDS 피처)',
    last_login_at DATETIME     NULL COMMENT '최근 로그인 일시',
    created_at    DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_device_uuid (device_uuid),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '등록 기기';

CREATE TABLE accessibility_settings (
    setting_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '설정 ID',
    user_id         BIGINT       NOT NULL COMMENT '사용자 ID',
    tts_speed       DECIMAL(3,2) NOT NULL COMMENT 'TTS 속도 0.50~2.00',
    tts_voice       VARCHAR(50)  NOT NULL COMMENT 'TTS 음색',
    font_scale      DECIMAL(3,2) NOT NULL COMMENT '글자 배율',
    high_contrast   TINYINT      NOT NULL COMMENT '고대비 모드',
    haptic_enabled  TINYINT      NOT NULL COMMENT '햅틱 피드백',
    voice_only_mode TINYINT      NOT NULL COMMENT '완전 비시각 모드',
    updated_at      DATETIME     NOT NULL COMMENT '수정일시',
    PRIMARY KEY (setting_id),
    UNIQUE KEY uk_setting_user (user_id),
    CONSTRAINT fk_setting_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '접근성 설정';

CREATE TABLE openbanking_connections (
    connection_id BIGINT        NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
    user_id       BIGINT        NOT NULL COMMENT '사용자 ID',
    user_seq_no   VARCHAR(50)   NOT NULL COMMENT '금결원 사용자일련번호',
    access_token  VARCHAR(1024) NOT NULL COMMENT '액세스 토큰(암호화)',
    refresh_token VARCHAR(1024) NULL COMMENT '리프레시 토큰(암호화)',
    expires_at    DATETIME      NOT NULL COMMENT '토큰 만료일시',
    scope         VARCHAR(200)  NULL COMMENT '권한 범위',
    status        VARCHAR(20)   NOT NULL COMMENT 'ACTIVE/EXPIRED/REVOKED',
    connected_at  DATETIME      NOT NULL COMMENT '연결일시',
    PRIMARY KEY (connection_id),
    UNIQUE KEY uk_conn_user_seq (user_seq_no),
    CONSTRAINT fk_conn_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '오픈뱅킹 연결';

CREATE TABLE accounts (
    account_id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '계좌 ID',
    user_id            BIGINT       NOT NULL COMMENT '사용자 ID',
    connection_id      BIGINT       NULL COMMENT '오픈뱅킹 연결 ID',
    fintech_use_num    VARCHAR(50)  NOT NULL COMMENT '핀테크이용번호',
    bank_code          VARCHAR(10)  NOT NULL COMMENT '은행 코드',
    bank_name          VARCHAR(50)  NOT NULL COMMENT '은행명',
    account_num_masked VARCHAR(255) NOT NULL COMMENT '계좌번호(마스킹/암호화)',
    account_alias      VARCHAR(50)  NULL COMMENT '음성 별칭(예: 월급통장)',
    account_type       VARCHAR(20)  NOT NULL COMMENT 'DEPOSIT/SAVING',
    is_primary         TINYINT      NOT NULL COMMENT '주계좌 여부',
    is_active          TINYINT      NOT NULL COMMENT '활성 여부',
    created_at         DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_fintech (fintech_use_num),
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_account_conn FOREIGN KEY (connection_id) REFERENCES openbanking_connections (connection_id)
) COMMENT '연결 계좌';

CREATE TABLE balance_snapshots (
    snapshot_id      BIGINT   NOT NULL AUTO_INCREMENT COMMENT '스냅샷 ID',
    account_id       BIGINT   NOT NULL COMMENT '계좌 ID',
    balance_amount   BIGINT   NOT NULL COMMENT '잔액',
    available_amount BIGINT   NOT NULL COMMENT '출금가능액',
    fetched_at       DATETIME NOT NULL COMMENT '조회일시',
    PRIMARY KEY (snapshot_id),
    CONSTRAINT fk_snapshot_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) COMMENT '잔액 스냅샷';

CREATE TABLE voice_sessions (
    session_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '세션 ID',
    user_id    BIGINT      NOT NULL COMMENT '사용자 ID',
    device_id  BIGINT      NULL COMMENT '기기 ID',
    channel        VARCHAR(20) NOT NULL COMMENT 'APP/PHONE',
    status         VARCHAR(30) NOT NULL COMMENT '세션 상태',
    pending_intent VARCHAR(40) NULL COMMENT '재질문·확인 대기 중인 의도',
    pending_slots  JSON        NULL COMMENT '보관 슬롯',
    retry_count    INT         NOT NULL COMMENT '재질문 횟수',
    expires_at     DATETIME    NOT NULL COMMENT '슬롯 만료 시각',
    started_at     DATETIME    NOT NULL COMMENT '시작일시',
    ended_at       DATETIME    NULL COMMENT '종료일시',
    PRIMARY KEY (session_id),
    CONSTRAINT fk_vsession_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_vsession_device FOREIGN KEY (device_id) REFERENCES devices (device_id)
) COMMENT '음성 세션';

CREATE TABLE voice_commands (
    command_id     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '명령 ID',
    session_id     BIGINT       NOT NULL COMMENT '세션 ID',
    user_id        BIGINT       NOT NULL COMMENT '사용자 ID',
    audio_uri      VARCHAR(500) NULL COMMENT '오디오 원본 URI',
    stt_text       TEXT         NULL COMMENT 'STT 변환 텍스트',
    stt_confidence DECIMAL(5,4) NULL COMMENT 'STT 신뢰도',
    intent         VARCHAR(40)  NOT NULL COMMENT 'BALANCE/TRANSFER/HISTORY/CONFIRM/CANCEL/UNKNOWN (GUARDIAN·SETTING은 예약값)',
    entities       JSON         NULL COMMENT '추출 엔티티',
    nlu_confidence DECIMAL(5,4) NULL COMMENT 'NLU 신뢰도',
    response_text  TEXT         NULL COMMENT 'TTS 응답 문구',
    status         VARCHAR(20)  NOT NULL COMMENT 'SUCCESS/CLARIFY/FAILED',
    processing_ms  INT          NULL COMMENT '처리 소요(ms)',
    created_at     DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (command_id),
    CONSTRAINT fk_vcmd_session FOREIGN KEY (session_id) REFERENCES voice_sessions (session_id),
    CONSTRAINT fk_vcmd_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '음성 명령';

CREATE TABLE transactions (
    transaction_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '거래 ID',
    account_id           BIGINT       NOT NULL COMMENT '계좌 ID',
    tran_type            VARCHAR(10)  NOT NULL COMMENT 'IN/OUT',
    amount               BIGINT       NOT NULL COMMENT '거래금액',
    balance_after        BIGINT       NULL COMMENT '거래후 잔액',
    counterparty_name    VARCHAR(100) NULL COMMENT '상대방명',
    counterparty_account VARCHAR(255) NULL COMMENT '상대방 계좌(암호화)',
    category             VARCHAR(30)  NULL COMMENT '음성 필터용 분류',
    tran_datetime        DATETIME     NOT NULL COMMENT '거래일시',
    memo                 VARCHAR(200) NULL COMMENT '메모',
    source               VARCHAR(20)  NOT NULL COMMENT 'OPENBANKING/INTERNAL',
    created_at           DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (transaction_id),
    CONSTRAINT fk_tran_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) COMMENT '거래 내역';

CREATE TABLE transfer_recipients (
    recipient_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '수취인 ID',
    user_id             BIGINT       NOT NULL COMMENT '사용자 ID',
    nickname            VARCHAR(50)  NOT NULL COMMENT '음성 호출명(예: 엄마)',
    bank_code           VARCHAR(10)  NOT NULL COMMENT '은행 코드',
    account_num         VARCHAR(255) NOT NULL COMMENT '계좌번호(암호화)',
    holder_name         VARCHAR(50)  NOT NULL COMMENT '예금주명',
    transfer_count      INT          NOT NULL COMMENT '누적 이체 횟수(FDS 피처)',
    last_transferred_at DATETIME     NULL COMMENT '최근 이체일시',
    created_at          DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (recipient_id),
    UNIQUE KEY uk_recipient_user_nick (user_id, nickname),
    CONSTRAINT fk_recipient_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '자주 쓰는 수취인';

CREATE TABLE transfers (
    transfer_id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '이체 ID',
    user_id          BIGINT       NOT NULL COMMENT '사용자 ID',
    from_account_id  BIGINT       NOT NULL COMMENT '출금 계좌 ID',
    recipient_id     BIGINT       NULL COMMENT '수취인 ID',
    voice_command_id BIGINT       NULL COMMENT '음성 명령 ID',
    to_bank_code     VARCHAR(10)  NOT NULL COMMENT '입금 은행 코드',
    to_account_num   VARCHAR(255) NOT NULL COMMENT '입금 계좌번호(암호화)',
    to_holder_name   VARCHAR(50)  NOT NULL COMMENT '입금 예금주명',
    amount           BIGINT       NOT NULL COMMENT '이체금액',
    status           VARCHAR(30)  NOT NULL COMMENT 'PENDING/RISK_REVIEW/COMPLETED/BLOCKED/FAILED/CANCELED',
    idempotency_key  VARCHAR(64)  NOT NULL COMMENT '중복 발화 방지 키',
    fail_reason      VARCHAR(200) NULL COMMENT '실패 사유',
    requested_at     DATETIME     NOT NULL COMMENT '요청일시',
    completed_at     DATETIME     NULL COMMENT '완료일시',
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_transfer_idem (idempotency_key),
    CONSTRAINT fk_transfer_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_transfer_account FOREIGN KEY (from_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_transfer_recipient FOREIGN KEY (recipient_id) REFERENCES transfer_recipients (recipient_id),
    CONSTRAINT fk_transfer_vcmd FOREIGN KEY (voice_command_id) REFERENCES voice_commands (command_id)
) COMMENT '이체 요청';

CREATE TABLE fds_assessments (
    assessment_id BIGINT        NOT NULL AUTO_INCREMENT COMMENT '평가 ID',
    transfer_id   BIGINT        NOT NULL COMMENT '이체 ID',
    user_id       BIGINT        NOT NULL COMMENT '사용자 ID',
    model_version VARCHAR(50)   NOT NULL COMMENT '모델 버전',
    anomaly_score DECIMAL(10,6) NOT NULL COMMENT '이상치 점수',
    risk_level    VARCHAR(10)   NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    decision      VARCHAR(30)   NOT NULL COMMENT 'ALLOW/ALLOW_WITH_ALERT/BLOCK',
    features      JSON          NULL COMMENT '모델 입력 피처 스냅샷',
    latency_ms    INT           NULL COMMENT '평가 소요(ms)',
    evaluated_at  DATETIME      NOT NULL COMMENT '평가일시',
    PRIMARY KEY (assessment_id),
    UNIQUE KEY uk_fds_transfer (transfer_id),
    CONSTRAINT fk_fds_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (transfer_id),
    CONSTRAINT fk_fds_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT 'FDS 위험 평가';

CREATE TABLE fds_rules (
    rule_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '룰 ID',
    rule_code      VARCHAR(50)  NOT NULL COMMENT '룰 코드',
    description    VARCHAR(200) NOT NULL COMMENT '룰 설명',
    condition_expr VARCHAR(500) NOT NULL COMMENT '조건식',
    risk_weight    DECIMAL(5,2) NOT NULL COMMENT '위험 가중치',
    is_active      TINYINT      NOT NULL COMMENT '활성 여부',
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_rule_code (rule_code)
) COMMENT 'FDS 룰';

CREATE TABLE fds_assessment_rules (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'ID',
    assessment_id BIGINT        NOT NULL COMMENT '평가 ID',
    rule_id       BIGINT        NOT NULL COMMENT '룰 ID',
    matched       TINYINT       NOT NULL COMMENT '매칭 여부',
    contribution  DECIMAL(10,6) NULL COMMENT '점수 기여도',
    PRIMARY KEY (id),
    CONSTRAINT fk_far_assessment FOREIGN KEY (assessment_id) REFERENCES fds_assessments (assessment_id),
    CONSTRAINT fk_far_rule FOREIGN KEY (rule_id) REFERENCES fds_rules (rule_id)
) COMMENT 'FDS 평가-룰 매핑';

CREATE TABLE user_transfer_profiles (
    user_id                 BIGINT        NOT NULL COMMENT '사용자 ID',
    avg_amount              BIGINT        NOT NULL COMMENT '평균 이체금액',
    max_amount              BIGINT        NOT NULL COMMENT '최대 이체금액',
    stddev_amount           DECIMAL(15,2) NOT NULL COMMENT '이체금액 표준편차',
    common_hours            JSON          NULL COMMENT '주 이체 시간대',
    transfer_count_30d      INT           NOT NULL COMMENT '30일 이체 건수',
    distinct_recipients_30d INT           NOT NULL COMMENT '30일 수취인 수',
    updated_at              DATETIME      NOT NULL COMMENT '수정일시',
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '사용자 이체 행동 프로필';

CREATE TABLE guardian_links (
    link_id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
    protectee_user_id BIGINT       NOT NULL COMMENT '피보호자 사용자 ID',
    guardian_user_id  BIGINT       NULL COMMENT '보호자 사용자 ID(가입 후 바인딩)',
    guardian_name     VARCHAR(50)  NOT NULL COMMENT '보호자명',
    guardian_phone    VARCHAR(255) NOT NULL COMMENT '보호자 전화번호(암호화)',
    relation          VARCHAR(30)  NULL COMMENT '관계(자녀/배우자 등)',
    status            VARCHAR(20)  NOT NULL COMMENT 'REQUESTED/ACTIVE/REJECTED/REVOKED',
    invite_token      VARCHAR(64)  NOT NULL COMMENT '초대 토큰',
    invite_expires_at DATETIME     NOT NULL COMMENT '초대 만료일시',
    permission_scope  JSON         NULL COMMENT '권한 범위',
    requested_at      DATETIME     NOT NULL COMMENT '요청일시',
    accepted_at       DATETIME     NULL COMMENT '수락일시',
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_glink_token (invite_token),
    CONSTRAINT fk_glink_protectee FOREIGN KEY (protectee_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_glink_guardian FOREIGN KEY (guardian_user_id) REFERENCES users (user_id)
) COMMENT '보호자 연결';

CREATE TABLE notifications (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    user_id         BIGINT       NULL COMMENT '수신 사용자 ID(미가입 보호자면 NULL)',
    link_id         BIGINT       NULL COMMENT '보호자 연결 ID',
    transfer_id     BIGINT       NULL COMMENT '관련 이체 ID',
    channel         VARCHAR(20)  NOT NULL COMMENT 'SMS/PUSH',
    template_code   VARCHAR(50)  NOT NULL COMMENT 'GUARDIAN_INVITE / RISK_TRANSFER_ALERT / BLOCKED_TRANSFER_ALERT',
    target_phone    VARCHAR(255) NULL COMMENT '수신 번호(암호화)',
    payload         JSON         NULL COMMENT '치환 데이터',
    status          VARCHAR(20)  NOT NULL COMMENT 'QUEUED/SENT/FAILED',
    provider_msg_id VARCHAR(100) NULL COMMENT '발송사 메시지 ID',
    sent_at         DATETIME     NULL COMMENT '발송일시',
    created_at      DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (notification_id),
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_noti_link FOREIGN KEY (link_id) REFERENCES guardian_links (link_id),
    CONSTRAINT fk_noti_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (transfer_id)
) COMMENT '알림 발송';

CREATE TABLE audit_logs (
    log_id        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '로그 ID',
    user_id       BIGINT       NULL COMMENT '사용자 ID',
    actor_type    VARCHAR(20)  NOT NULL COMMENT 'USER/GUARDIAN/SYSTEM',
    action        VARCHAR(50)  NOT NULL COMMENT '행위',
    resource_type VARCHAR(50)  NULL COMMENT '대상 리소스 유형',
    resource_id   BIGINT       NULL COMMENT '대상 리소스 ID',
    ip            VARCHAR(45)  NULL COMMENT 'IP 주소',
    user_agent    VARCHAR(300) NULL COMMENT 'User-Agent',
    detail        JSON         NULL COMMENT '상세 내용',
    created_at    DATETIME     NOT NULL COMMENT '생성일시',
    PRIMARY KEY (log_id),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) COMMENT '감사 로그';
