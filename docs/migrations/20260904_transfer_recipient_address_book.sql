-- =====================================================================
-- transfer_recipients : 주소록 항목과 일회성 송금 대상을 구분하고,
--                       계좌 유일성 기준에 은행을 넣는다
--
-- 적용 순서를 반드시 지킨다. 3단계에서 기존 데이터를 확인하지 않고
-- 5단계를 실행하면 UNIQUE 추가가 실패하고 앞 단계만 적용된 상태로 남는다.
--
--   1단계  컬럼 추가 (address_book, verified_at)
--   2단계  nickname NULL 허용
--   3단계  기존 데이터 점검 (읽기만 한다)
--   4단계  기존 행을 주소록 항목으로 표시
--   5단계  계좌 UNIQUE 를 (user_id, bank_code, account_num_hash) 로 교체
--
-- 되돌릴 때는 역순으로 지운다. verified_at 을 지우면 어떤 계좌가 확인된
-- 것인지 알 수 없게 되므로, 롤백은 코드까지 함께 되돌릴 때만 한다.
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1단계. 컬럼 추가
--
-- address_book : 사용자가 이름을 지어 주소록에 올린 항목인지.
--                계좌번호로 한 번 보낼 때 만들어지는 거래 상대 신원 행은 FALSE 다.
-- verified_at  : 예금주조회로 계좌를 확인한 시각. NULL 이면 이 행으로 이체하지 않는다.
--
-- 기본값을 FALSE / NULL 로 두는 것이 중요하다. 기존 행은 접두어만 맞춰
-- 저장됐을 수 있어 확인된 것으로 볼 근거가 없다.
-- ---------------------------------------------------------------------
ALTER TABLE transfer_recipients
    ADD COLUMN address_book BOOLEAN  NOT NULL DEFAULT FALSE
        COMMENT '사용자가 이름을 지어 등록한 주소록 항목인지',
    ADD COLUMN verified_at  DATETIME NULL
        COMMENT '예금주조회로 계좌를 확인한 시각. NULL 이면 이체 대상이 아니다';


-- ---------------------------------------------------------------------
-- 2단계. nickname NULL 허용
--
-- 일회성 송금 대상에는 이름이 없다. 예전에는 "국민은행 6789" 처럼 지어냈고
-- 겹치면 "(2)" 를 붙였는데, 사용자가 짓지 않은 이름이라 부를 수도 지울 수도
-- 없었다. UNIQUE (user_id, nickname) 아래에서 MySQL 은 NULL 을 중복으로 보지
-- 않으므로 여러 행이 함께 있을 수 있다.
-- ---------------------------------------------------------------------
ALTER TABLE transfer_recipients
    MODIFY COLUMN nickname VARCHAR(50) NULL COMMENT '음성 호출명. 주소록 항목에만 있다';


-- ---------------------------------------------------------------------
-- 3단계. 기존 데이터 점검 — 읽기만 한다
--
-- (1) 은행을 포함해도 여전히 중복인 계좌가 있는지 본다.
--     결과가 있으면 5단계 UNIQUE 추가가 실패한다. 지우지 말고 어느 행을
--     남길지 정한 뒤 손으로 정리한다 — transfers.recipient_id 와
--     fds_assessments 가 이 행을 참조하고 있어, 지우면 거래 이력이 끊긴다.
-- ---------------------------------------------------------------------
SELECT user_id, bank_code, account_num_hash, COUNT(*) AS duplicated
FROM transfer_recipients
GROUP BY user_id, bank_code, account_num_hash
HAVING COUNT(*) > 1;

-- (2) 자동 생성 별칭으로 보이는 행. 참고용이며 이것만으로 판단하지 않는다.
--     검증 여부는 모양이 아니라 예금주조회 결과로만 정해진다.
SELECT recipient_id, user_id, bank_code, nickname, transfer_count
FROM transfer_recipients
WHERE nickname REGEXP '[0-9]{4}( \\([0-9]+\\))?$';


-- ---------------------------------------------------------------------
-- 4단계. 기존 행을 주소록 항목으로 표시
--
-- 지금까지 이 테이블에 있던 행은 전부 목록에 보이고 이름으로 불렸다.
-- 그 동작을 유지하려면 주소록 항목으로 둬야 한다.
--
-- verified_at 은 채우지 않는다. 확인된 적이 없기 때문이다. 이 행으로
-- 이체하려 하면 애플리케이션이 예금주조회를 다시 하고, 확인되면 그때
-- verified_at 을 채운다. 확인되지 않으면 TRANSFER_4012 로 안내하고 멈춘다.
-- ---------------------------------------------------------------------
UPDATE transfer_recipients
SET address_book = TRUE
WHERE nickname IS NOT NULL;


-- ---------------------------------------------------------------------
-- 5단계. 계좌 UNIQUE 교체
--
-- 계좌번호는 은행 안에서만 유일하다. 은행이 빠져 있으면 다른 은행의 같은
-- 번호가 중복으로 막히고, 반대로 음성 경로는 은행이 다르면 새 행을 만들려다
-- UNIQUE 위반으로 500 이 났다. 조회 기준과 제약을 같은 열로 맞춘다.
-- ---------------------------------------------------------------------
ALTER TABLE transfer_recipients
    DROP INDEX uk_recipient_user_account,
    ADD UNIQUE KEY uk_recipient_user_bank_account (user_id, bank_code, account_num_hash);
