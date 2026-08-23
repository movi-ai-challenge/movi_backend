-- 사용자별 계좌 음성 별칭 중복을 DB에서도 원자적으로 차단한다.
-- MySQL UNIQUE는 NULL을 여러 건 허용하므로 별칭이 아직 없는 계좌는 영향을 받지 않는다.
ALTER TABLE accounts
    ADD UNIQUE KEY uk_account_user_alias (user_id, account_alias);
