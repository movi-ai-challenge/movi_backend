-- 카카오 로그인은 더 이상 전화번호를 회원 정보로 받지 않는다.
-- users.phone/phone_hash는 카카오 최초 가입 시점에는 NULL이고,
-- PIN 최초 등록(POST /api/v1/auth/pin/register)에서 채운다.
-- 배포 전 운영 MySQL에 한 번 적용한다.

ALTER TABLE users
    MODIFY COLUMN phone VARCHAR(255) NULL
        COMMENT 'AES 암호화. 카카오 가입 시점엔 없고 PIN 등록 시 채움';
