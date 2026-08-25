-- 보호자 등록을 초대 승인 방식에서 즉시 등록 방식으로 변경.
-- 회원가입(온보딩)에서 보호자 전화번호를 입력하면 확인 절차 없이 바로 ACTIVE로 연결한다.
-- 배포 전 운영 MySQL에 한 번 적용한다.

-- REQUESTED/REJECTED로 남아 있던 행은 이 변경으로 더 이상 의미가 없다.
-- 아직 아무도 승인하지 않은 요청이므로 실제로 연결된 적이 없다 — 삭제한다.
-- 운영 데이터에 REQUESTED/REJECTED 행이 있는지 먼저 확인하고, 있다면 삭제 전에 팀에 공유한다.
DELETE FROM guardian_links WHERE status IN ('REQUESTED', 'REJECTED');

ALTER TABLE guardian_links
    DROP KEY uk_glink_token,
    DROP COLUMN invite_token,
    DROP COLUMN invite_expires_at,
    CHANGE COLUMN requested_at linked_at DATETIME NOT NULL COMMENT '연결일시',
    DROP COLUMN accepted_at,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/REVOKED';
