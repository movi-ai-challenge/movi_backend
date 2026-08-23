ALTER TABLE notifications
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '실패한 발송 시도 횟수' AFTER sent_at,
    ADD COLUMN next_retry_at DATETIME NULL COMMENT '다음 재시도 시각' AFTER retry_count,
    DROP INDEX idx_noti_status,
    ADD INDEX idx_noti_retry (status, next_retry_at);
