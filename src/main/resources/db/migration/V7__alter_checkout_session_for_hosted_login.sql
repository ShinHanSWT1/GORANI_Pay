ALTER TABLE pay_checkout_sessions
    ALTER COLUMN pay_user_id DROP NOT NULL,
    ALTER COLUMN pay_account_id DROP NOT NULL;

ALTER TABLE pay_checkout_sessions
    ADD COLUMN integration_type VARCHAR(30) NOT NULL DEFAULT 'INTERNAL_TOKEN';

ALTER TABLE pay_checkout_sessions
    DROP CONSTRAINT IF EXISTS pay_checkout_sessions_external_order_id_key;

ALTER TABLE pay_checkout_sessions
    ADD CONSTRAINT uk_checkout_sessions_merchant_order UNIQUE (merchant_code, external_order_id);

CREATE INDEX IF NOT EXISTS idx_pay_checkout_sessions_merchant_order
    ON pay_checkout_sessions(merchant_code, external_order_id);
