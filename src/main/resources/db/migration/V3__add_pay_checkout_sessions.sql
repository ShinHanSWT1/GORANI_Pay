CREATE TABLE pay_checkout_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_token VARCHAR(80) NOT NULL UNIQUE,
    merchant_code VARCHAR(50) NOT NULL,
    pay_user_id BIGINT NOT NULL,
    pay_account_id BIGINT NOT NULL,
    external_order_id VARCHAR(80) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    amount INTEGER NOT NULL,
    success_url VARCHAR(500) NOT NULL,
    fail_url VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    auto_charge_used BOOLEAN NOT NULL DEFAULT FALSE,
    payment_id BIGINT,
    error_message TEXT,
    expires_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_checkout_sessions_user FOREIGN KEY (pay_user_id) REFERENCES pay_users(id),
    CONSTRAINT fk_checkout_sessions_account FOREIGN KEY (pay_account_id) REFERENCES pay_accounts(id),
    CONSTRAINT fk_checkout_sessions_payment FOREIGN KEY (payment_id) REFERENCES pay_payments(id)
);

CREATE INDEX idx_pay_checkout_sessions_pay_user ON pay_checkout_sessions(pay_user_id);
CREATE INDEX idx_pay_checkout_sessions_status ON pay_checkout_sessions(status);
