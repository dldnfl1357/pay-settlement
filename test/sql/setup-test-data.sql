-- ============================================================
-- Payment System Load Test - 테스트 데이터 초기화
-- ============================================================

-- 1. 기존 데이터 삭제 (주의: 운영 환경에서 실행 금지)
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ledger_entries;
TRUNCATE TABLE payments;
TRUNCATE TABLE wallets;
TRUNCATE TABLE merchants;
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 가맹점 생성
INSERT INTO merchants (id, name, fee_rate, created_at) VALUES
(1, '테스트가맹점A', 0.025, NOW()),
(2, '테스트가맹점B', 0.020, NOW());

-- 3. 월렛 100개 생성 (각 100만원 초기 잔액)
DELIMITER //
CREATE PROCEDURE IF NOT EXISTS create_test_wallets()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        INSERT INTO wallets (id, user_id, balance, version, created_at, updated_at)
        VALUES (i, i, 1000000.00, 0, NOW(), NOW());
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL create_test_wallets();
DROP PROCEDURE IF EXISTS create_test_wallets;

-- 4. 초기 상태 확인
SELECT
    'MERCHANTS' AS entity,
    COUNT(*) AS count,
    NULL AS total_balance
FROM merchants
UNION ALL
SELECT
    'WALLETS' AS entity,
    COUNT(*) AS count,
    SUM(balance) AS total_balance
FROM wallets;

-- 5. 테스트 초기 잔액 스냅샷 저장
CREATE TABLE IF NOT EXISTS test_initial_balance (
    wallet_id BIGINT PRIMARY KEY,
    initial_balance DECIMAL(15,2),
    snapshot_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

TRUNCATE TABLE test_initial_balance;
INSERT INTO test_initial_balance (wallet_id, initial_balance)
SELECT id, balance FROM wallets;

SELECT '테스트 데이터 초기화 완료' AS status;
