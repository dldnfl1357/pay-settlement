-- ============================================================
-- Payment System Load Test - 대용량 테스트 데이터 생성
-- Wallet: 100,000개 (잔액 100만원 고정)
-- Merchant: 10,000개 (수수료율 1% 고정)
-- ============================================================

-- 1. 기존 데이터 삭제
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE ledger_entries;
TRUNCATE TABLE payments;
TRUNCATE TABLE wallets;
TRUNCATE TABLE merchants;
SET FOREIGN_KEY_CHECKS = 1;

-- 2. 성능 최적화 설정
SET autocommit = 0;
SET unique_checks = 0;
SET foreign_key_checks = 0;

-- ============================================================
-- 3. Merchant 10,000개 생성
-- ============================================================
DROP PROCEDURE IF EXISTS create_merchants;
DELIMITER //
CREATE PROCEDURE create_merchants()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE total INT DEFAULT 10000;

    WHILE i <= total DO
        INSERT INTO merchants (name, business_number, fee_rate, created_at)
        VALUES (
            CONCAT('가맹점_', LPAD(i, 5, '0')),
            CONCAT('BIZ-', LPAD(i, 10, '0')),
            0.0100,  -- 1% 고정 수수료율
            NOW()
        );

        -- 1000건마다 커밋
        IF i MOD batch_size = 0 THEN
            COMMIT;
            SELECT CONCAT('Merchants created: ', i, ' / ', total) AS progress;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END//
DELIMITER ;

CALL create_merchants();
DROP PROCEDURE IF EXISTS create_merchants;

SELECT CONCAT('Merchant 생성 완료: ', COUNT(*), '개') AS status FROM merchants;

-- ============================================================
-- 4. Wallet 100,000개 생성
-- ============================================================
DROP PROCEDURE IF EXISTS create_wallets;
DELIMITER //
CREATE PROCEDURE create_wallets()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 5000;
    DECLARE total INT DEFAULT 100000;

    WHILE i <= total DO
        INSERT INTO wallets (user_id, user_name, balance, version, created_at, updated_at)
        VALUES (
            i,
            CONCAT('사용자_', LPAD(i, 6, '0')),
            1000000.00,  -- 100만원 고정 잔액
            0,
            NOW(),
            NOW()
        );

        -- 5000건마다 커밋
        IF i MOD batch_size = 0 THEN
            COMMIT;
            SELECT CONCAT('Wallets created: ', i, ' / ', total) AS progress;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END//
DELIMITER ;

CALL create_wallets();
DROP PROCEDURE IF EXISTS create_wallets;

SELECT CONCAT('Wallet 생성 완료: ', COUNT(*), '개') AS status FROM wallets;

-- ============================================================
-- 5. 성능 설정 복원
-- ============================================================
SET unique_checks = 1;
SET foreign_key_checks = 1;
SET autocommit = 1;

-- ============================================================
-- 6. 생성 결과 확인
-- ============================================================
SELECT '=== 데이터 생성 결과 ===' AS title;

SELECT
    'MERCHANTS' AS entity,
    COUNT(*) AS count,
    MIN(fee_rate) AS min_fee_rate,
    MAX(fee_rate) AS max_fee_rate,
    AVG(fee_rate) AS avg_fee_rate
FROM merchants;

SELECT
    'WALLETS' AS entity,
    COUNT(*) AS count,
    MIN(balance) AS min_balance,
    MAX(balance) AS max_balance,
    AVG(balance) AS avg_balance,
    SUM(balance) AS total_balance
FROM wallets;

-- ============================================================
-- 7. 초기 잔액 스냅샷 저장 (정합성 검증용)
-- ============================================================
CREATE TABLE IF NOT EXISTS test_initial_balance (
    wallet_id BIGINT PRIMARY KEY,
    initial_balance DECIMAL(15,2),
    snapshot_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

TRUNCATE TABLE test_initial_balance;
INSERT INTO test_initial_balance (wallet_id, initial_balance)
SELECT id, balance FROM wallets;

SELECT CONCAT('초기 잔액 스냅샷 저장 완료: ', COUNT(*), '개') AS status
FROM test_initial_balance;

SELECT '=== 부하 테스트 데이터 준비 완료 ===' AS final_status;
