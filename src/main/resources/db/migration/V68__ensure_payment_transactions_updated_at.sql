SET @payment_transactions_updated_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_transactions'
      AND column_name = 'updated_at'
);

SET @payment_transactions_updated_at_sql = IF(
    @payment_transactions_updated_at_exists = 0,
    'ALTER TABLE payment_transactions ADD COLUMN updated_at DATETIME(6) NULL',
    'SELECT 1'
);

PREPARE payment_transactions_updated_at_stmt FROM @payment_transactions_updated_at_sql;
EXECUTE payment_transactions_updated_at_stmt;
DEALLOCATE PREPARE payment_transactions_updated_at_stmt;
