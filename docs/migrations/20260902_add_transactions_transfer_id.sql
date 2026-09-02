-- 거래내역에서 FDS 판정을 보여주기 위한 연결
--
-- 배경: transactions 와 fds_assessments 를 잇는 길이 없었다. 평가는 transfer_id 로
--       달려 있는데 transactions 에는 그 값이 없어, 거래내역 화면이 "이 거래가
--       위험하다고 잡혔는지"를 알 수 없었다.
--
--       금액·시각으로 맞추는 방법도 있으나, 같은 초에 같은 금액을 두 번 보내면
--       엉뚱한 거래에 위험 표시가 붙는다. 돈이 걸린 화면에서 그런 오표시는
--       하지 않는 편이 낫다.
--
-- !! 운영은 ddl-auto: validate 다. 애플리케이션 배포보다 이 스크립트를 먼저 적용해야 한다.

-- 우리 서비스를 거치지 않은 거래(은행에서 내려받은 입출금)는 transfer 가 없다.
-- 그래서 NULL 을 허용한다.
ALTER TABLE transactions
    ADD COLUMN transfer_id BIGINT NULL COMMENT '이 거래를 만든 이체. 외부 유입 거래는 NULL' AFTER account_id,
    ADD KEY idx_transaction_transfer (transfer_id),
    ADD CONSTRAINT fk_transaction_transfer FOREIGN KEY (transfer_id) REFERENCES transfers (transfer_id);

-- 이미 쌓인 거래를 이체와 이어 붙인다. 우리가 만든 이체는 출금 계좌·금액·완료
-- 시각이 그대로 거래에 복사되므로 세 값이 모두 같은 건만 연결한다.
UPDATE transactions t
JOIN transfers tr
  ON tr.from_account_id = t.account_id
 AND tr.amount = t.amount
 AND tr.completed_at = t.tran_datetime
SET t.transfer_id = tr.transfer_id
WHERE t.transfer_id IS NULL;
