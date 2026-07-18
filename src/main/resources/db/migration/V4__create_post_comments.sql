CREATE TABLE post_comment
(
    id           UUID        NOT NULL,
    post_id      UUID        NOT NULL,
    author_id    UUID        NOT NULL,
    text_content TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_post_comment PRIMARY KEY (id),
    CONSTRAINT fk_post_comment_post
        FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_comment_author
        FOREIGN KEY (author_id) REFERENCES identity_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_comment_text
        CHECK (
            text_content !~ U&'^[[:space:][:cntrl:]\00A0\2007\202F]*$'
            AND CHAR_LENGTH(text_content) <= 5000
        )
);

CREATE INDEX idx_post_comment_post_created_id ON post_comment (post_id, created_at, id);
CREATE INDEX idx_post_comment_author ON post_comment (author_id);

ALTER TABLE post DROP CONSTRAINT ck_post_comment_count;

ALTER TABLE post
    ADD CONSTRAINT ck_post_comment_count
        CHECK (comment_count >= 0) NOT VALID;
