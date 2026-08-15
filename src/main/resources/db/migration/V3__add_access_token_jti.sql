ALTER TABLE sessions
    ADD COLUMN access_token_jti VARCHAR(36);

CREATE INDEX idx_sessions_access_token_jti ON sessions (access_token_jti);
