CREATE TABLE accounts (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX idx_accounts_username ON accounts (username);
