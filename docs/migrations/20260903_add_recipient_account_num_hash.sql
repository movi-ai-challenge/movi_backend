-- 상대방 중복 등록 차단 (같은 계좌를 다른 이름으로 등록하는 것)
--
-- 배경: 상대방 등록이 별칭 중복만 확인하고 계좌 중복은 확인하지 않았다. 같은 계좌를
--       "엄마"·"어머니"로 각각 등록하면 transfer_count 가 이름별로 쪼개져 FDS 의
--       "처음 보내는 상대" 판정이 흐려진다.
--
--       account_num 은 무작위 IV 로 암호화(AES/GCM)되어 같은 계좌라도 암호문이 매번
--       다르다. 그래서 users.phone_hash 와 같은 패턴으로 검색 해시를 따로 둔다.
--
-- !! 운영은 ddl-auto: validate 다. 애플리케이션 배포보다 1단계를 먼저 적용해야 한다.
--    순서가 뒤바뀌면 엔티티와 스키마가 어긋나 기동 자체가 실패한다.
--
-- !! 이 파일은 한 번에 다 실행하는 스크립트가 아니다. 1 → 2 → 3 순서로 나눠 적용한다.
--    2단계(백필)는 SQL 로 할 수 없다 -- 암호문 복호화와 HMAC 계산에 모두 애플리케이션
--    키가 필요하고, MySQL 에는 대응하는 함수가 없다.


-- ---------------------------------------------------------------------------
-- 1단계. 컬럼 추가 (NULL 허용) — 애플리케이션 배포 전
-- ---------------------------------------------------------------------------
-- 여기서 NOT NULL 로 만들지 않는다. 기존 행을 채울 방법이 SQL 에 없어서, 그대로
-- 실행하면 MySQL 이 빈 문자열을 채워 넣는다. 그 '' 끼리 3단계 UNIQUE 에 걸린다.
--
-- Hibernate 의 validate 는 컬럼 존재와 타입만 보고 NULL 허용 여부는 보지 않는다.
-- 그래서 엔티티가 nullable = false 여도 이 상태로 기동한다.

ALTER TABLE transfer_recipients
    ADD COLUMN account_num_hash VARCHAR(64) NULL
        COMMENT '계좌번호 중복 확인용 HMAC-SHA256' AFTER account_num;


-- ---------------------------------------------------------------------------
-- 2단계. 기존 행 백필 — 애플리케이션이 수행한다 (SQL 아님)
-- ---------------------------------------------------------------------------
-- 백필 플래그를 켜고 애플리케이션을 한 번 기동하면 RecipientAccountHashBackfill 이
-- 해시가 빈 행을 찾아 채우고, 이미 중복된 계좌가 있으면 로그로 알려 준다.
--
--   ./gradlew bootRun --args='--spring.profiles.active=local \
--       --movi.migration.recipient-account-hash.enabled=true'
--
-- 실행 인자로 넘기는 편이 낫다. application-local.yml 은 gitignore 대상이라 사람마다
-- 손으로 고쳐야 하고, 백필이 끝난 뒤 되돌리는 것도 잊기 쉽다. 인자로 주면 그 기동에만
-- 적용되고 다음 기동은 자동으로 꺼진 상태다.
--
-- 남은 행이 없으면 아무 일도 하지 않으므로 두 번 돌려도 안전하다.
--
-- 확인:
--   SELECT COUNT(*) FROM transfer_recipients WHERE account_num_hash IS NULL;
--   -- 0 이어야 3단계로 넘어갈 수 있다.


-- ---------------------------------------------------------------------------
-- 3단계. 제약 조건 적용 — 백필과 중복 정리가 끝난 뒤
-- ---------------------------------------------------------------------------
-- 먼저 이미 중복 등록된 계좌가 있는지 본다. 있으면 UNIQUE 추가가 실패한다.
--
--   SELECT user_id, account_num_hash, COUNT(*) AS cnt,
--          GROUP_CONCAT(recipient_id) AS recipient_ids,
--          GROUP_CONCAT(nickname)     AS nicknames
--   FROM transfer_recipients
--   WHERE account_num_hash IS NOT NULL
--   GROUP BY user_id, account_num_hash
--   HAVING cnt > 1;
--
-- 나온 행은 사용자가 직접 만든 데이터다. 자동으로 지우지 않는다. 어느 이름을 남길지
-- 정한 뒤(보통 transfer_count 가 큰 쪽) 나머지를 지운다. 지우기 전에 그 수취인을
-- 참조하는 이체가 있는지 확인한다 -- transfers.recipient_id 가 FK 로 물려 있다.
--
--   SELECT recipient_id, COUNT(*) FROM transfers
--   WHERE recipient_id IN (...) GROUP BY recipient_id;

ALTER TABLE transfer_recipients
    MODIFY COLUMN account_num_hash VARCHAR(64) NOT NULL
        COMMENT '계좌번호 중복 확인용 HMAC-SHA256',
    ADD UNIQUE KEY uk_recipient_user_account (user_id, account_num_hash);
