SELECT
    (SELECT COUNT(*) FROM payments WHERE status = 'APPROVED') AS approved_payments,
    (SELECT COUNT(DISTINCT payment_id) FROM ledger_entries
     WHERE entry_type = 'DEBIT' AND account_type = 'WALLET') AS ledger_records;