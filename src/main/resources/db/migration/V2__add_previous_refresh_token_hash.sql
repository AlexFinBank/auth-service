ALTER TABLE sessions
    ADD COLUMN previous_refresh_token_hash VARCHAR(255);

CREATE INDEX idx_sessions_previous_refresh_token_hash ON sessions (previous_refresh_token_hash);
