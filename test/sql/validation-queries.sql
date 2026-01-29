-- ============================================================
-- Payment System Load Test - 데이터 정합성 검증 쿼리
-- ============================================================

-- ============================================================
-- 1. 테스트 전 초기화
-- ============================================================

-- 1.1 초기 잔액 스냅샷 저장 (테스트 전 실행)
CREATE TABLE IF NOT EXISTS test_initial_balance (
    wallet_id BIGINT PRIMARY KEY,
    initial_balance DECIMAL(15,2),
    snapshot_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 초기 잔액 저장
TRUNCATE TABLE test_initial_balance;
INSERT INTO test_initial_balance (wallet_id, initial_balance)
SELECT id, balance FROM wallets;

-- 1.3 초기 상태 확인
SELECT
    COUNT(*) AS wallet_count,
    SUM(initial_balance) AS total_initial_balance
FROM test_initial_balance;


-- ============================================================
-- 2. 월렛 잔액 정합성 검증
-- ============================================================

-- 2.1 월렛별 잔액 검증 (원장 기준 vs 실제 잔액)
SELECT
    w.id AS wallet_id,
    tib.initial_balance,
    w.balance AS current_balance,
    COALESCE(ledger.debit_total, 0) AS ledger_debit,
    COALESCE(ledger.credit_total, 0) AS ledger_credit,
    tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0) AS expected_balance,
    CASE
        WHEN w.balance = tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0)
        THEN 'OK'
        ELSE 'MISMATCH'
    END AS status
FROM wallets w
JOIN test_initial_balance tib ON w.id = tib.wallet_id
LEFT JOIN (
    SELECT
        account_id,
        SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS debit_total,
        SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
    FROM ledger_entries
    WHERE account_type = 'WALLET'
    GROUP BY account_id
) ledger ON w.id = ledger.account_id
ORDER BY w.id;

-- 2.2 불일치 월렛만 조회
SELECT
    w.id AS wallet_id,
    w.balance AS current_balance,
    tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0) AS expected_balance,
    w.balance - (tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0)) AS difference
FROM wallets w
JOIN test_initial_balance tib ON w.id = tib.wallet_id
LEFT JOIN (
    SELECT
        account_id,
        SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS debit_total,
        SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
    FROM ledger_entries
    WHERE account_type = 'WALLET'
    GROUP BY account_id
) ledger ON w.id = ledger.account_id
WHERE w.balance != tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0);


-- ============================================================
-- 3. 복식부기 정합성 검증
-- ============================================================

-- 3.1 전체 DEBIT/CREDIT 합계 비교
SELECT
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS total_debit,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS total_credit,
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) -
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS difference,
    CASE
        WHEN SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) =
             SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END)
        THEN 'OK'
        ELSE 'MISMATCH'
    END AS status
FROM ledger_entries;

-- 3.2 트랜잭션별 DEBIT/CREDIT 쌍 검증
SELECT
    transaction_id,
    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS debit_amount,
    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS credit_amount,
    COUNT(*) AS entry_count,
    CASE
        WHEN SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) =
             SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END)
        THEN 'OK'
        ELSE 'MISMATCH'
    END AS status
FROM ledger_entries
GROUP BY transaction_id
HAVING SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) !=
       SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END);


-- ============================================================
-- 4. 이중 지불 검증
-- ============================================================

-- 4.1 동일 멱등성 키로 여러 결제 생성 여부
SELECT
    idempotency_key,
    COUNT(*) AS payment_count,
    GROUP_CONCAT(id ORDER BY id) AS payment_ids,
    GROUP_CONCAT(status ORDER BY id) AS statuses
FROM payments
GROUP BY idempotency_key
HAVING COUNT(*) > 1;

-- 4.2 동일 주문번호로 여러 결제 승인 여부 (APPROVED 상태만)
SELECT
    order_id,
    COUNT(*) AS approved_count,
    GROUP_CONCAT(id ORDER BY id) AS payment_ids,
    SUM(amount) AS total_amount
FROM payments
WHERE status = 'APPROVED'
GROUP BY order_id
HAVING COUNT(*) > 1;


-- ============================================================
-- 5. 결제 상태 집계
-- ============================================================

-- 5.1 상태별 집계
SELECT
    status,
    COUNT(*) AS count,
    SUM(amount) AS total_amount,
    AVG(amount) AS avg_amount,
    MIN(created_at) AS first_created,
    MAX(created_at) AS last_created
FROM payments
GROUP BY status
ORDER BY status;

-- 5.2 시간대별 결제 추이 (분 단위)
SELECT
    DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS minute,
    COUNT(*) AS total_count,
    SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
    SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending
FROM payments
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')
ORDER BY minute;


-- ============================================================
-- 6. 승인 결제 vs 원장 기록 일치 검증
-- ============================================================

-- 6.1 승인된 결제 중 원장 기록 누락 검사
SELECT
    p.id AS payment_id,
    p.status,
    p.amount,
    p.approved_at,
    'LEDGER_MISSING' AS issue
FROM payments p
LEFT JOIN ledger_entries le ON p.id = le.payment_id AND le.entry_type = 'DEBIT'
WHERE p.status = 'APPROVED' AND le.id IS NULL;

-- 6.2 원장에 있지만 결제가 없는 경우 검사
SELECT
    le.payment_id,
    le.transaction_id,
    le.amount,
    'PAYMENT_MISSING' AS issue
FROM ledger_entries le
LEFT JOIN payments p ON le.payment_id = p.id
WHERE p.id IS NULL;

-- 6.3 승인 결제 수 vs 원장 기록 수
SELECT
    (SELECT COUNT(*) FROM payments WHERE status = 'APPROVED') AS approved_payments,
    (SELECT COUNT(DISTINCT payment_id) FROM ledger_entries WHERE entry_type = 'DEBIT' AND account_type = 'WALLET') AS ledger_debit_records,
    CASE
        WHEN (SELECT COUNT(*) FROM payments WHERE status = 'APPROVED') =
             (SELECT COUNT(DISTINCT payment_id) FROM ledger_entries WHERE entry_type = 'DEBIT' AND account_type = 'WALLET')
        THEN 'OK'
        ELSE 'MISMATCH'
    END AS status;


-- ============================================================
-- 7. Saga 보상 검증
-- ============================================================

-- 7.1 FAILED 상태 결제의 보상 처리 검증
SELECT
    p.id AS payment_id,
    p.wallet_id,
    p.amount AS payment_amount,
    p.status,
    COALESCE(comp.credit_amount, 0) AS compensation_credit,
    CASE
        WHEN p.status = 'FAILED' AND COALESCE(comp.credit_amount, 0) > 0 THEN 'COMPENSATED'
        WHEN p.status = 'FAILED' AND COALESCE(comp.credit_amount, 0) = 0 THEN 'NOT_COMPENSATED'
        ELSE 'N/A'
    END AS compensation_status
FROM payments p
LEFT JOIN (
    SELECT payment_id, SUM(amount) AS credit_amount
    FROM ledger_entries
    WHERE entry_type = 'CREDIT' AND account_type = 'WALLET'
      AND description LIKE '%보상%'
    GROUP BY payment_id
) comp ON p.id = comp.payment_id
WHERE p.status = 'FAILED';

-- 7.2 잔액 차감 후 PG 실패 케이스 추적
SELECT
    p.id AS payment_id,
    p.wallet_id,
    p.amount,
    p.status,
    debit.debit_exists,
    credit.credit_exists,
    CASE
        WHEN p.status = 'FAILED' AND debit.debit_exists = 1 AND credit.credit_exists = 0
        THEN 'COMPENSATION_MISSING'
        ELSE 'OK'
    END AS issue
FROM payments p
LEFT JOIN (
    SELECT payment_id, 1 AS debit_exists
    FROM ledger_entries
    WHERE entry_type = 'DEBIT' AND account_type = 'WALLET'
    GROUP BY payment_id
) debit ON p.id = debit.payment_id
LEFT JOIN (
    SELECT payment_id, 1 AS credit_exists
    FROM ledger_entries
    WHERE entry_type = 'CREDIT' AND account_type = 'WALLET' AND description LIKE '%보상%'
    GROUP BY payment_id
) credit ON p.id = credit.payment_id
WHERE p.status = 'FAILED';


-- ============================================================
-- 8. 성능 관련 통계
-- ============================================================

-- 8.1 결제 처리 시간 분석 (승인 기준)
SELECT
    COUNT(*) AS total_approved,
    AVG(TIMESTAMPDIFF(MICROSECOND, created_at, approved_at)) / 1000 AS avg_approval_time_ms,
    MIN(TIMESTAMPDIFF(MICROSECOND, created_at, approved_at)) / 1000 AS min_approval_time_ms,
    MAX(TIMESTAMPDIFF(MICROSECOND, created_at, approved_at)) / 1000 AS max_approval_time_ms
FROM payments
WHERE status = 'APPROVED' AND approved_at IS NOT NULL;

-- 8.2 월렛별 결제 분포
SELECT
    wallet_id,
    COUNT(*) AS payment_count,
    SUM(CASE WHEN status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count,
    SUM(CASE WHEN status = 'APPROVED' THEN amount ELSE 0 END) AS approved_amount
FROM payments
GROUP BY wallet_id
ORDER BY payment_count DESC
LIMIT 20;


-- ============================================================
-- 9. 종합 검증 리포트
-- ============================================================

SELECT '=== 종합 검증 리포트 ===' AS report;

SELECT 'WALLET_BALANCE' AS check_type,
    CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM wallets w
            JOIN test_initial_balance tib ON w.id = tib.wallet_id
            LEFT JOIN (
                SELECT account_id,
                    SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END) AS debit_total,
                    SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END) AS credit_total
                FROM ledger_entries WHERE account_type = 'WALLET' GROUP BY account_id
            ) ledger ON w.id = ledger.account_id
            WHERE w.balance != tib.initial_balance - COALESCE(ledger.debit_total, 0) + COALESCE(ledger.credit_total, 0)
        ) THEN 'PASS' ELSE 'FAIL'
    END AS result
UNION ALL
SELECT 'DOUBLE_ENTRY' AS check_type,
    CASE
        WHEN (SELECT SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END)) =
             (SELECT SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END))
        FROM ledger_entries THEN 'PASS' ELSE 'FAIL'
    END AS result
UNION ALL
SELECT 'IDEMPOTENCY' AS check_type,
    CASE
        WHEN NOT EXISTS (
            SELECT 1 FROM payments GROUP BY idempotency_key HAVING COUNT(*) > 1
        ) THEN 'PASS' ELSE 'FAIL'
    END AS result
UNION ALL
SELECT 'PAYMENT_LEDGER_MATCH' AS check_type,
    CASE
        WHEN (SELECT COUNT(*) FROM payments WHERE status = 'APPROVED') =
             (SELECT COUNT(DISTINCT payment_id) FROM ledger_entries WHERE entry_type = 'DEBIT' AND account_type = 'WALLET')
        THEN 'PASS' ELSE 'FAIL'
    END AS result;


-- ============================================================
-- 10. 정리
-- ============================================================

-- 테스트 테이블 삭제 (선택적)
-- DROP TABLE IF EXISTS test_initial_balance;
