ALTER TABLE pay_checkout_sessions
    ADD COLUMN entry_mode VARCHAR(30) NOT NULL DEFAULT 'MERCHANT_REDIRECT',
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'REDIRECT',
    ADD COLUMN one_time_token VARCHAR(120),
    ADD COLUMN token_expires_at TIMESTAMP;

CREATE INDEX idx_pay_checkout_sessions_entry_mode ON pay_checkout_sessions(entry_mode);
CREATE INDEX idx_pay_checkout_sessions_one_time_token ON pay_checkout_sessions(one_time_token);
