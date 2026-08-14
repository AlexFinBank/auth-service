CREATE TABLE users
(
    id                     VARCHAR(36)  NOT NULL,
    created_by             VARCHAR(255),
    updated_by             VARCHAR(255),
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP,
    username               VARCHAR(100) NOT NULL,
    email                  VARCHAR(255) NOT NULL,
    password               VARCHAR(255) NOT NULL,
    role                   VARCHAR(20)  NOT NULL,
    status                 VARCHAR(20)  NOT NULL,
    failed_login_attempts  INTEGER      NOT NULL DEFAULT 0,
    locked_until           TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE sessions
(
    id                  VARCHAR(36)  NOT NULL,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP,
    user_id             VARCHAR(36)  NOT NULL,
    refresh_token_hash  VARCHAR(255) NOT NULL,
    device_label        VARCHAR(255),
    ip_address          VARCHAR(45),
    status              VARCHAR(20)  NOT NULL,
    last_used_at        TIMESTAMP,
    expires_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_sessions PRIMARY KEY (id),
    CONSTRAINT uq_sessions_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_sessions_user_id ON sessions (user_id);
CREATE INDEX idx_sessions_user_id_status ON sessions (user_id, status);

CREATE TABLE password_reset_tokens
(
    id          VARCHAR(36)  NOT NULL,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,
    user_id     VARCHAR(36)  NOT NULL,
    token_hash  VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);
