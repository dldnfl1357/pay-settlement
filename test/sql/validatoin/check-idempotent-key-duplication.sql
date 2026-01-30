SELECT idempotency_key, COUNT(*) AS cnt, GROUP_CONCAT(id) AS payment_ids
FROM payments
GROUP BY idempotency_key
HAVING COUNT(*) > 1;