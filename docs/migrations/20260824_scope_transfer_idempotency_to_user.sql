-- 멱등성 키를 사용자 범위로 제한한다.
-- 같은 사용자의 중복 이체는 막고, 서로 다른 사용자가 우연히 같은 키를 써도 결과를 공유하지 않는다.
ALTER TABLE transfers
    DROP INDEX uk_transfer_idem,
    ADD UNIQUE KEY uk_transfer_user_idem (user_id, idempotency_key);
