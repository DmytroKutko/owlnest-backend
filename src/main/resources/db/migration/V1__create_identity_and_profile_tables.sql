CREATE TABLE identity_account
(
    id               UUID         PRIMARY KEY,
    provider         VARCHAR(32)  NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(320),
    email_verified   BOOLEAN      NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    last_seen_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_identity_account_provider_subject UNIQUE (provider, external_subject)
);

CREATE INDEX idx_identity_account_email ON identity_account (email);

CREATE TABLE profile
(
    account_id   UUID         PRIMARY KEY,
    username     VARCHAR(50)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    bio          VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_profile_account
        FOREIGN KEY (account_id) REFERENCES identity_account (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_profile_username_lower ON profile (LOWER(username));
