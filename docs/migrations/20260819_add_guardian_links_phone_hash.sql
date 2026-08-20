-- 7.7 중복 연결 방지 배포 전에 운영 MySQL에 한 번 적용한다.
--
-- guardian_phone은 무작위 IV로 암호화되어 있어 같은 번호라도 암호문이 매번 다르다.
-- 즉 암호문 비교로는 "이미 초대한 번호"를 판별할 수 없다.
-- users.phone_hash와 동일하게 HMAC-SHA256 검색용 컬럼을 둔다.
--
-- 기존 행의 guardian_phone_hash는 NULL로 남는다. 복호화 없이는 채울 수 없고,
-- MVP 데이터 규모에서는 새 요청부터 적용해도 문제가 없다고 판단했다.
-- 과거 데이터까지 채우려면 별도 배치에서 복호화 후 UPDATE 한다.

ALTER TABLE guardian_links
    ADD COLUMN guardian_phone_hash VARCHAR(64) NULL
        COMMENT '보호자 전화번호 중복 확인용 HMAC-SHA256' AFTER guardian_phone,
    ADD KEY idx_glink_protectee_phone_status (protectee_user_id, guardian_phone_hash, status);

-- relation은 자유 문자열에서 enum 이름 저장으로 바뀐다.
-- 기존 한국어 값이 있다면 아래로 맞춘다.
UPDATE guardian_links SET relation = 'CHILD'         WHERE relation = '자녀';
UPDATE guardian_links SET relation = 'SPOUSE'        WHERE relation = '배우자';
UPDATE guardian_links SET relation = 'SOCIAL_WORKER' WHERE relation = '사회복지사';
UPDATE guardian_links SET relation = 'OTHER'
 WHERE relation IS NOT NULL
   AND relation NOT IN ('CHILD', 'SPOUSE', 'SOCIAL_WORKER', 'OTHER');

-- 값 변환만 하고 코멘트를 두면 스키마가 어긋난다. 다음 사람이 컬럼 설명을 믿을 수 없게 된다.
ALTER TABLE guardian_links
    MODIFY COLUMN relation VARCHAR(30) NULL
        COMMENT 'CHILD/SPOUSE/SOCIAL_WORKER/OTHER';
