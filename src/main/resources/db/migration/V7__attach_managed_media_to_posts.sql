ALTER TABLE post_media
    ALTER COLUMN url DROP NOT NULL,
    ADD COLUMN managed_media_id UUID,
    ADD COLUMN managed_media_purpose VARCHAR(32)
        GENERATED ALWAYS AS (
            CASE WHEN managed_media_id IS NULL THEN NULL ELSE 'POST_IMAGE' END
        ) STORED,
    ADD CONSTRAINT uq_post_media_managed_media_id UNIQUE (managed_media_id),
    ADD CONSTRAINT ck_post_media_exactly_one_source
        CHECK (NUM_NONNULLS(url, managed_media_id) = 1),
    ADD CONSTRAINT ck_post_media_managed_type
        CHECK (managed_media_id IS NULL OR media_type = 'IMAGE'),
    ADD CONSTRAINT fk_post_media_managed_media
        FOREIGN KEY (managed_media_id, managed_media_purpose)
            REFERENCES managed_media (id, purpose) ON DELETE RESTRICT;
