-- 일반 로그인(아이디 + 비밀번호) 도입
--
-- 배경: 계정을 만드는 경로가 카카오 하나뿐이었다. users 에는 로그인 아이디가,
--       user_credentials 에는 비밀번호가 없었고 PIN 은 카카오 가입자의 재로그인 수단이다.
--
-- !! 운영은 ddl-auto: validate 다. 애플리케이션 배포보다 이 스크립트를 먼저 적용해야 한다.
--    순서가 뒤바뀌면 엔티티와 스키마가 어긋나 기동 자체가 실패한다.

-- login_id 는 개인정보가 아니라 사용자가 직접 정한 식별자다. 검색 해시 없이 평문 UNIQUE 로 둔다.
-- 카카오 전용 가입자는 NULL 이며, MySQL 의 UNIQUE 는 NULL 중복을 허용하므로 그대로 공존한다.
ALTER TABLE users
    ADD COLUMN login_id VARCHAR(30) NULL COMMENT '일반 로그인 아이디(소문자 정규화). 카카오 전용 가입자는 NULL' AFTER name,
    ADD UNIQUE KEY uk_users_login_id (login_id);

ALTER TABLE user_credentials
    ADD COLUMN password_hash VARCHAR(255) NULL COMMENT 'BCrypt. 일반 로그인 비밀번호. 카카오·PIN 전용 사용자는 NULL' AFTER pin_hash;

-- 일반 가입자는 PIN 을 등록하지 않고도 계정이 성립한다. 기존 행은 값이 있으므로 영향 없다.
ALTER TABLE user_credentials
    MODIFY COLUMN pin_hash VARCHAR(255) NULL COMMENT 'BCrypt. 카카오 가입자가 등록한 PIN. 일반 가입자는 NULL';
