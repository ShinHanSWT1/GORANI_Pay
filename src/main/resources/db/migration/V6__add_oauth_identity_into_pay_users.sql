ALTER TABLE pay_users
    ADD COLUMN oauth_provider VARCHAR(20),
    ADD COLUMN oauth_user_id VARCHAR(80);

CREATE UNIQUE INDEX idx_pay_users_oauth_identity
    ON pay_users(oauth_provider, oauth_user_id);
