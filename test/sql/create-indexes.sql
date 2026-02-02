-- 인덱스 확인 및 생성 스크립트

-- 현재 인덱스 확인
SHOW INDEX FROM payments;
SHOW INDEX FROM wallets;
SHOW INDEX FROM ledger_entries;

-- Payment 인덱스
CREATE INDEX IF NOT EXISTS idx_idempotency ON payments(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_merchant_created ON payments(merchant_id, created_at);
CREATE INDEX IF NOT EXISTS idx_wallet_id ON payments(wallet_id);
CREATE INDEX IF NOT EXISTS idx_status ON payments(status);

-- Wallet 인덱스 (PK는 자동 생성됨)
CREATE INDEX IF NOT EXISTS idx_user_id ON wallets(user_id);

-- LedgerEntry 인덱스
CREATE INDEX IF NOT EXISTS idx_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX IF NOT EXISTS idx_payment_id ON ledger_entries(payment_id);
CREATE INDEX IF NOT EXISTS idx_account ON ledger_entries(account_type, account_id);

-- 인덱스 생성 확인
SHOW INDEX FROM payments;
SHOW INDEX FROM wallets;
SHOW INDEX FROM ledger_entries;
