CREATE TABLE post
(
    id            UUID         NOT NULL,
    author_id     UUID         NOT NULL,
    title         VARCHAR(200),
    description   TEXT         NOT NULL,
    post_type     VARCHAR(32)  NOT NULL,
    like_count    BIGINT       NOT NULL DEFAULT 0,
    comment_count BIGINT       NOT NULL DEFAULT 0,
    repost_count  BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT pk_post PRIMARY KEY (id),
    CONSTRAINT fk_post_author
        FOREIGN KEY (author_id) REFERENCES identity_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_title
        CHECK (title IS NULL OR (
            title <> ''
            AND title !~ U&'^[[:space:][:cntrl:]\00A0\2007\202F]'
            AND title !~ U&'[[:space:][:cntrl:]\00A0\2007\202F]$'
            AND CHAR_LENGTH(title) <= 200
        )),
    CONSTRAINT ck_post_description
        CHECK (
            description !~ U&'^[[:space:][:cntrl:]\00A0\2007\202F]*$'
            AND CHAR_LENGTH(description) <= 20000
        ),
    CONSTRAINT ck_post_type
        CHECK (post_type IN ('PERSONAL', 'COMMUNITY')),
    CONSTRAINT ck_post_like_count CHECK (like_count >= 0),
    CONSTRAINT ck_post_comment_count CHECK (comment_count = 0),
    CONSTRAINT ck_post_repost_count CHECK (repost_count >= 0)
);

CREATE INDEX idx_post_author ON post (author_id);

CREATE TABLE post_label
(
    post_id  UUID        NOT NULL,
    position SMALLINT    NOT NULL,
    label    VARCHAR(50) NOT NULL,
    CONSTRAINT pk_post_label PRIMARY KEY (post_id, position),
    CONSTRAINT fk_post_label_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_label_position CHECK (position BETWEEN 0 AND 4),
    CONSTRAINT ck_post_label_value
        CHECK (
            label <> ''
            AND label !~ U&'^[[:space:][:cntrl:]\00A0\2007\202F]'
            AND label !~ U&'[[:space:][:cntrl:]\00A0\2007\202F]$'
            AND CHAR_LENGTH(label) <= 50
        )
);

CREATE TABLE post_media
(
    post_id    UUID          NOT NULL,
    position   SMALLINT      NOT NULL,
    media_type VARCHAR(16)   NOT NULL,
    url        VARCHAR(2048) NOT NULL,
    CONSTRAINT pk_post_media PRIMARY KEY (post_id, position),
    CONSTRAINT fk_post_media_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_media_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT ck_post_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_post_media_url
        CHECK (
            CHAR_LENGTH(url) BETWEEN 1 AND 2048
            AND url ~* '^https://[^/?#]+'
        )
);

CREATE TABLE post_like
(
    post_id    UUID        NOT NULL,
    account_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_post_like PRIMARY KEY (post_id, account_id),
    CONSTRAINT fk_post_like_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_account
        FOREIGN KEY (account_id) REFERENCES identity_account (id) ON DELETE RESTRICT
);

CREATE INDEX idx_post_like_account ON post_like (account_id);

CREATE TABLE post_bookmark
(
    post_id    UUID        NOT NULL,
    account_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_post_bookmark PRIMARY KEY (post_id, account_id),
    CONSTRAINT fk_post_bookmark_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_bookmark_account
        FOREIGN KEY (account_id) REFERENCES identity_account (id) ON DELETE RESTRICT
);

CREATE INDEX idx_post_bookmark_account ON post_bookmark (account_id);

CREATE TABLE post_repost
(
    post_id    UUID        NOT NULL,
    account_id UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_post_repost PRIMARY KEY (post_id, account_id),
    CONSTRAINT fk_post_repost_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_repost_account
        FOREIGN KEY (account_id) REFERENCES identity_account (id) ON DELETE RESTRICT
);

CREATE INDEX idx_post_repost_account ON post_repost (account_id);
