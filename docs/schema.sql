-- Movi (Voice-First Inclusive Banking) MVP 스키마
-- MySQL 8.0 / utf8mb4

SET NAMES utf8mb4;

-- =====================================================
-- 1. 사용자 · 인증
-- =====================================================

CREATE TABLE users (
    user_id      BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(50)  NOT NULL,
    phone        VARCHAR(255) NULL     COMMENT 'AES 암호화. 카카오 가입 시점엔 없고 PIN 등록 시 채움',
    phone_hash   VARCHAR(64)  NULL COMMENT '전화번호 중복 확인용 HMAC-SHA256',
    birth_date   DATE         NULL,
    user_type    VARCHAR(30)  NOT NULL DEFAULT 'GENERAL' COMMENT 'SENIOR/VISUALLY_IMPAIRED/GENERAL',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'  COMMENT 'ACTIVE/DORMANT/WITHDRAWN',
    token_version BIGINT      NOT NULL DEFAULT 0 COMMENT '로그아웃 시 JWT 일괄 무효화 버전',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_phone_hash (phone_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE oauth_accounts (
    oauth_id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL DEFAULT 'KAKAO',
    provider_user_id VARCHAR(100) NOT NULL,
    access_token     VARCHAR(512) NULL COMMENT 'AES 암호화',
    refresh_token    VARCHAR(512) NULL COMMENT 'AES 암호화',
    token_expires_at DATETIME     NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (oauth_id),
    UNIQUE KEY uk_oauth_provider_user (provider, provider_user_id),
    KEY idx_oauth_user (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_credentials (
    credential_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    pin_hash          VARCHAR(255) NOT NULL COMMENT 'BCrypt',
    biometric_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts   INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME     NULL,
    pin_updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (credential_id),
    UNIQUE KEY uk_credential_user (user_id),
    CONSTRAINT fk_credential_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE devices (
    device_id     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    device_uuid   VARCHAR(100) NOT NULL,
    device_model  VARCHAR(100) NULL,
    os_version    VARCHAR(50)  NULL,
    push_token    VARCHAR(512) NULL,
    is_trusted    BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'FDS 신뢰 기기 피처',
    last_login_at DATETIME     NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id),
    UNIQUE KEY uk_device_uuid (device_uuid),
    KEY idx_device_user (user_id),
    CONSTRAINT fk_device_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accessibility_settings (
    setting_id      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    tts_speed       DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '0.50 ~ 2.00',
    tts_voice       VARCHAR(50)  NOT NULL DEFAULT 'DEFAULT',
    font_scale      DECIMAL(3,2) NOT NULL DEFAULT 1.00,
    high_contrast   BOOLEAN      NOT NULL DEFAULT FALSE,
    haptic_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    voice_only_mode BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '완전 비시각 모드',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_id),
    UNIQUE KEY uk_setting_user (user_id),
    CONSTRAINT fk_setting_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 2. 오픈뱅킹 · 계좌
-- =====================================================

CREATE TABLE openbanking_connections (
    connection_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    user_seq_no   VARCHAR(50)  NOT NULL COMMENT '금결원 사용자일련번호',
    access_token  VARCHAR(1024) NOT NULL COMMENT 'AES 암호화',
    refresh_token VARCHAR(1024) NULL COMMENT 'AES 암호화',
    expires_at    DATETIME     NOT NULL,
    scope         VARCHAR(200) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED/REVOKED',
    connected_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (connection_id),
    UNIQUE KEY uk_conn_user_seq (user_seq_no),
    KEY idx_conn_user (user_id),
    CONSTRAINT fk_conn_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounts (
    account_id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    connection_id      BIGINT       NULL,
    fintech_use_num    VARCHAR(50)  NOT NULL COMMENT '핀테크이용번호',
    bank_code          VARCHAR(10)  NOT NULL,
    bank_name          VARCHAR(50)  NOT NULL,
    account_num_masked VARCHAR(255) NOT NULL,
    account_alias      VARCHAR(50)  NULL COMMENT '음성 별칭 (예: 월급통장)',
    account_type       VARCHAR(20)  NOT NULL DEFAULT 'DEPOSIT',
    is_primary         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_account_fintech (fintech_use_num),
    UNIQUE KEY uk_account_user_alias (user_id, account_alias),
    KEY idx_account_user_active (user_id, is_active),
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_account_conn FOREIGN KEY (connection_id) REFERENCES openbanking_connections (connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE balance_snapshots (
    snapshot_id      BIGINT   NOT NULL AUTO_INCREMENT,
    account_id       BIGINT   NOT NULL,
    balance_amount   BIGINT   NOT NULL,
    available_amount BIGINT   NOT NULL,
    fetched_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (snapshot_id),
    KEY idx_snapshot_account_time (account_id, fetched_at DESC),
    CONSTRAINT fk_snapshot_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 3. 음성 (transfers 보다 먼저 생성 — FK 참조)
-- =====================================================

CREATE TABLE voice_sessions (
    session_id BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    device_id  BIGINT      NULL,
    channel        VARCHAR(20) NOT NULL DEFAULT 'APP' COMMENT 'APP/PHONE',
    status         VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'
                   COMMENT 'ACTIVE/CLARIFYING/AWAITING_CONFIRMATION/PROCESSING/COMPLETED/CANCELED/EXPIRED',
    pending_intent VARCHAR(40) NULL COMMENT '재질문·확인 대기 중인 의도',
    pending_slots  JSON        NULL COMMENT '{"recipient":"엄마","amount":null}',
    retry_count    INT         NOT NULL DEFAULT 0 COMMENT '같은 슬롯 재질문 횟수 (최대 3)',
    expires_at     DATETIME    NOT NULL COMMENT '슬롯 만료 시각',
    started_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at       DATETIME    NULL,
    PRIMARY KEY (session_id),
    KEY idx_vsession_user (user_id, started_at DESC),
    KEY idx_vsession_status_exp (status, expires_at),
    CONSTRAINT fk_vsession_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_vsession_device FOREIGN KEY (device_id) REFERENCES devices (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE voice_commands (
    command_id     BIGINT       NOT NULL AUTO_INCREMENT,
    session_id     BIGINT       NOT NULL,
    user_id        BIGINT       NOT NULL,
    audio_uri      VARCHAR(500) NULL COMMENT 'S3 등 오디오 원본',
    stt_text       TEXT         NULL,
    stt_confidence DECIMAL(5,4) NULL,
    intent         VARCHAR(40)  NOT NULL DEFAULT 'UNKNOWN'
                   COMMENT 'BALANCE/TRANSFER/HISTORY/CONFIRM/CANCEL/UNKNOWN (GUARDIAN·SETTING은 예약값)',
    entities       JSON         NULL COMMENT '{"recipient":"엄마","amount":50000}',
    nlu_confidence DECIMAL(5,4) NULL,
    response_text  TEXT         NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/CLARIFY/FAILED',
    processing_ms  INT          NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (command_id),
    KEY idx_vcmd_user_time (user_id, created_at DESC),
    KEY idx_vcmd_intent_status (intent, status),
    CONSTRAINT fk_vcmd_session FOREIGN KEY (session_id) REFERENCES voice_sessions (session_id),
    CONSTRAINT fk_vcmd_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 4. 거래 · 이체
-- =====================================================

CREATE TABLE transactions (
    transaction_id       BIGINT       NOT NULL AUTO_INCREMENT,
    account_id           BIGINT       NOT NULL,
    tran_type            VARCHAR(10)  NOT NULL COMMENT 'IN/OUT',
    amount               BIGINT       NOT NULL,
    balance_after        BIGINT       NULL,
    counterparty_name    VARCHAR(100) NULL,
    counterparty_account VARCHAR(255) NULL,
    category             VARCHAR(30)  NULL COMMENT '음성 필터용 (식비/공과금 등)',
    tran_datetime        DATETIME     NOT NULL,
    memo                 VARCHAR(200) NULL,
    source               VARCHAR(20)  NOT NULL DEFAULT 'OPENBANKING',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (transaction_id),
    KEY idx_tran_account_time (account_id, tran_datetime DESC),
    KEY idx_tran_category (account_id, category),
    CONSTRAINT fk_tran_account FOREIGN KEY (account_id) REFERENCES accounts (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfer_recipients (
    recipient_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    nickname            VARCHAR(50)  NOT NULL COMMENT '음성 호출명 (예: 엄마)',
    bank_code           VARCHAR(10)  NOT NULL,
    account_num         VARCHAR(255) NOT NULL COMMENT 'AES 암호화',
    holder_name         VARCHAR(50)  NOT NULL,
    transfer_count      INT          NOT NULL DEFAULT 0,
    last_transferred_at DATETIME     NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (recipient_id),
    UNIQUE KEY uk_recipient_user_nick (user_id, nickname),
    CONSTRAINT fk_recipient_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transfers (
    transfer_id      BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    from_account_id  BIGINT       NOT NULL,
    recipient_id     BIGINT       NULL,
    voice_command_id BIGINT       NULL COMMENT '음성 트리거 추적',
    to_bank_code     VARCHAR(10)  NOT NULL,
    to_account_num   VARCHAR(255) NOT NULL COMMENT 'AES 암호화',
    to_holder_name   VARCHAR(50)  NOT NULL,
    amount           BIGINT       NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING'
                     COMMENT 'PENDING/RISK_REVIEW/COMPLETED/BLOCKED/FAILED/CANCELED',
    idempotency_key  VARCHAR(64)  NOT NULL COMMENT '중복 발화 방지',
    fail_reason      VARCHAR(200) NULL,
    requested_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     DATETIME     NULL,
    PRIMARY KEY (transfer_id),
    UNIQUE KEY uk_transfer_user_idem (user_id, idempotency_key),
    KEY idx_transfer_user_time (user_id, requested_at DESC),
    KEY idx_transfer_status (status),
    CONSTRAINT fk_transfer_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_transfer_account FOREIGN KEY (from_account_id) REFERENCES accounts (account_id),
    CONSTRAINT fk_transfer_recipient FOREIGN KEY (recipient_id) REFERENCES transfer_recipients (recipient_id),
    CONSTRAINT fk_transfer_vcmd FOREIGN KEY (voice_command_id) REFERENCES voice_commands (command_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 5. FDS
-- =====================================================

CREATE TABLE fds_assessments (
    assessment_id BIGINT        NOT NULL AUTO_INCREMENT,
    transfer_id   BIGINT        NOT NULL,
    user_id       BIGINT        NOT NULL,
    model_version VARCHAR(50)   NOT NULL COMMENT 'isolation-forest-v1',
    anomaly_score DECIMAL(10,6) NOT NULL,
    risk_level    VARCHAR(10)   NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    decision      VARCHAR(30)   NOT NULL COMMENT 'ALLOW/ALLOW_WITH_ALERT/BLOCK',
    features      JSON          NULL COMMENT '모델 입력 피처 스냅샷',
    latency_ms    INT           NULL,
    evaluated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assessment_id),
    UNIQUE KEY uk_fds_transfer (transfer_id),
    KEY idx_fds_user_time (user_id, evaluated_at DESC),
    KEY idx_fds_risk (risk_level, evaluated_at DESC),
    CONSTRAINT fk_fds_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (transfer_id),
    CONSTRAINT fk_fds_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fds_rules (
    rule_id        BIGINT       NOT NULL AUTO_INCREMENT,
    rule_code      VARCHAR(50)  NOT NULL,
    description    VARCHAR(200) NOT NULL,
    condition_expr VARCHAR(500) NOT NULL COMMENT 'SpEL 등 표현식',
    risk_weight    DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fds_assessment_rules (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    assessment_id BIGINT        NOT NULL,
    rule_id       BIGINT        NOT NULL,
    matched       BOOLEAN       NOT NULL,
    contribution  DECIMAL(10,6) NULL COMMENT '점수 기여도',
    PRIMARY KEY (id),
    KEY idx_far_assessment (assessment_id),
    CONSTRAINT fk_far_assessment FOREIGN KEY (assessment_id) REFERENCES fds_assessments (assessment_id),
    CONSTRAINT fk_far_rule FOREIGN KEY (rule_id) REFERENCES fds_rules (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_transfer_profiles (
    user_id                 BIGINT        NOT NULL,
    avg_amount              BIGINT        NOT NULL DEFAULT 0,
    max_amount              BIGINT        NOT NULL DEFAULT 0,
    stddev_amount           DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    common_hours            JSON          NULL COMMENT '[9,12,18] 주 이체 시간대',
    transfer_count_30d      INT           NOT NULL DEFAULT 0,
    distinct_recipients_30d INT           NOT NULL DEFAULT 0,
    updated_at              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 6. 보호자
-- =====================================================

CREATE TABLE guardian_links (
    link_id            BIGINT       NOT NULL AUTO_INCREMENT,
    protectee_user_id  BIGINT       NOT NULL COMMENT '피보호자 (앱 주 사용자)',
    guardian_user_id   BIGINT       NULL     COMMENT '보호자 가입 후 바인딩',
    guardian_name      VARCHAR(50)  NOT NULL,
    guardian_phone     VARCHAR(255) NOT NULL COMMENT 'AES 암호화',
    relation           VARCHAR(30)  NULL     COMMENT '자녀/배우자/사회복지사',
    status             VARCHAR(20)  NOT NULL DEFAULT 'REQUESTED'
                       COMMENT 'REQUESTED/ACTIVE/REJECTED/REVOKED',
    invite_token       VARCHAR(64)  NOT NULL,
    invite_expires_at  DATETIME     NOT NULL,
    permission_scope   JSON         NULL COMMENT '{"view_balance":true,"receive_alert":true}',
    requested_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at        DATETIME     NULL,
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_glink_token (invite_token),
    KEY idx_glink_protectee (protectee_user_id, status),
    KEY idx_glink_guardian (guardian_user_id, status),
    CONSTRAINT fk_glink_protectee FOREIGN KEY (protectee_user_id) REFERENCES users (user_id),
    CONSTRAINT fk_glink_guardian FOREIGN KEY (guardian_user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    notification_id BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NULL COMMENT '미가입 보호자면 NULL',
    link_id         BIGINT       NULL,
    transfer_id     BIGINT       NULL COMMENT '관련 이체 ID',
    channel         VARCHAR(20)  NOT NULL COMMENT 'SMS/PUSH',
    template_code   VARCHAR(50)  NOT NULL COMMENT 'GUARDIAN_INVITE / RISK_TRANSFER_ALERT / BLOCKED_TRANSFER_ALERT',
    target_phone    VARCHAR(255) NULL COMMENT 'AES 암호화',
    payload         JSON         NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED/SENT/FAILED',
    provider_msg_id VARCHAR(100) NULL,
    sent_at         DATETIME     NULL,
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '실패한 발송 시도 횟수',
    next_retry_at   DATETIME     NULL COMMENT '다음 재시도 시각',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id),
    KEY idx_noti_user (user_id, created_at DESC),
    KEY idx_noti_retry (status, next_retry_at),
    CONSTRAINT fk_noti_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_noti_link FOREIGN KEY (link_id) REFERENCES guardian_links (link_id),
    CONSTRAINT fk_noti_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 7. 공통
-- =====================================================

CREATE TABLE audit_logs (
    log_id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NULL,
    actor_type    VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT 'USER/GUARDIAN/SYSTEM',
    action        VARCHAR(50)  NOT NULL,
    resource_type VARCHAR(50)  NULL,
    resource_id   BIGINT       NULL,
    ip            VARCHAR(45)  NULL,
    user_agent    VARCHAR(300) NULL,
    detail        JSON         NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_audit_user_time (user_id, created_at DESC),
    KEY idx_audit_resource (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 초기 FDS 룰 데이터
-- =====================================================

INSERT INTO fds_rules (rule_code, description, condition_expr, risk_weight) VALUES
('FIRST_TIME_RECIPIENT', '처음 이체하는 수취인',        'recipient.transferCount == 0',              0.30),
('UNUSUAL_AMOUNT',       '평소 평균 대비 5배 초과 금액', 'amount > profile.avgAmount * 5',            0.35),
('UNUSUAL_HOUR',         '평소와 다른 시간대 (심야)',   'hour < 6 || hour > 23',                     0.20),
('UNTRUSTED_DEVICE',     '미등록 기기에서의 이체',      'device.isTrusted == false',                 0.25),
('LOW_STT_CONFIDENCE',   '음성 인식 신뢰도 저조',       'voiceCommand.sttConfidence < 0.75',         0.15),
('RAPID_SUCCESSION',     '단시간 연속 이체',            'recentTransferCount1h >= 3',                0.25),
('MAX_AMOUNT_EXCEEDED',  '기존 최대 이체액 초과',       'amount > profile.maxAmount',                0.20);
