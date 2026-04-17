ALTER TABLE pay_checkout_sessions
    ADD COLUMN point_amount INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN coupon_discount_amount INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pay_product_id BIGINT;

CREATE INDEX idx_pay_checkout_sessions_pay_product ON pay_checkout_sessions(pay_product_id);
