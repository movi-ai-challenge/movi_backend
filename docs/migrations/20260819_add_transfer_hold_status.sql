-- 고위험 이체를 즉시 차단하지 않고 본인 재확인을 받도록 정책이 바뀌면서
-- transfers.status에 HOLD(확인 대기)가 추가됐다.
--
-- 컬럼 타입은 VARCHAR(30) 그대로이므로 데이터 마이그레이션은 필요 없다.
-- 코멘트만 실제 상태 집합과 맞춘다. 코멘트가 어긋나 있으면 다음 사람이
-- "HOLD가 어디서 나온 값이지?"부터 추적해야 한다.

ALTER TABLE transfers
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/RISK_REVIEW/HOLD/COMPLETED/BLOCKED/FAILED/CANCELED';
