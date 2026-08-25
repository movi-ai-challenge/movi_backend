-- 카카오 로그인 배포 전에 운영 MySQL에 한 번 적용한다.
-- 기존 phone은 무작위 IV로 암호화되어 있으므로 직접 중복 검색에 사용할 수 없다.

ALTER TABLE users
    ADD COLUMN phone_hash VARCHAR(64) NULL
        COMMENT '전화번호 중복 확인용 HMAC-SHA256' AFTER phone,
    DROP INDEX uk_users_phone,
    ADD UNIQUE KEY uk_users_phone_hash (phone_hash);
