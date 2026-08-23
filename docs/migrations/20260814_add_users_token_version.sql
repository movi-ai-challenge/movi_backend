-- JWT 로그아웃 즉시 무효화를 위해 운영 MySQL에 한 번 적용한다.

ALTER TABLE users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0
        COMMENT '로그아웃 시 JWT 일괄 무효화 버전' AFTER status;
