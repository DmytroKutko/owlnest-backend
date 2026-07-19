CREATE TABLE managed_media
(
    id                       UUID         NOT NULL,
    owner_account_id         UUID         NOT NULL,
    purpose                  VARCHAR(32)  NOT NULL,
    object_key               VARCHAR(512) NOT NULL,
    declared_content_type    VARCHAR(127) NOT NULL,
    declared_size_bytes      BIGINT       NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    observed_content_type    VARCHAR(127),
    observed_size_bytes      BIGINT,
    object_etag              VARCHAR(255),
    upload_expires_at        TIMESTAMPTZ  NOT NULL,
    ready_at                 TIMESTAMPTZ,
    ready_expires_at         TIMESTAMPTZ,
    deletion_reason          VARCHAR(32),
    deletion_requested_at    TIMESTAMPTZ,
    cleanup_due_at           TIMESTAMPTZ,
    cleanup_lease_token      UUID,
    cleanup_lease_expires_at TIMESTAMPTZ,
    cleanup_attempt_count    INTEGER      NOT NULL DEFAULT 0,
    cleanup_next_attempt_at  TIMESTAMPTZ,
    cleanup_last_error_code  VARCHAR(64),
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    deleted_at               TIMESTAMPTZ,
    CONSTRAINT pk_managed_media PRIMARY KEY (id),
    CONSTRAINT uq_managed_media_object_key UNIQUE (object_key),
    CONSTRAINT uq_managed_media_id_purpose UNIQUE (id, purpose),
    CONSTRAINT fk_managed_media_owner
        FOREIGN KEY (owner_account_id) REFERENCES identity_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_managed_media_purpose
        CHECK (purpose IN ('AVATAR', 'POST_IMAGE', 'POST_VIDEO')),
    CONSTRAINT ck_managed_media_declared_metadata
        CHECK (
            (purpose = 'AVATAR'
                AND declared_content_type IN ('image/jpeg', 'image/png', 'image/webp')
                AND declared_size_bytes BETWEEN 1 AND 10485760)
            OR (purpose = 'POST_IMAGE'
                AND declared_content_type IN ('image/jpeg', 'image/png', 'image/webp')
                AND declared_size_bytes BETWEEN 1 AND 20971520)
            OR (purpose = 'POST_VIDEO'
                AND declared_content_type IN ('video/mp4', 'video/quicktime')
                AND declared_size_bytes BETWEEN 1 AND 262144000)
        ),
    CONSTRAINT ck_managed_media_status
        CHECK (status IN ('AWAITING_UPLOAD', 'READY', 'ACTIVE', 'DELETION_PENDING', 'DELETED')),
    CONSTRAINT ck_managed_media_deletion_reason
        CHECK (deletion_reason IN (
            'UPLOAD_EXPIRED',
            'READY_EXPIRED',
            'SUPERSEDED',
            'DETACHED',
            'USER_REMOVED',
            'USER_CANCELLED'
        )),
    CONSTRAINT ck_managed_media_object_key
        CHECK (CHAR_LENGTH(object_key) BETWEEN 1 AND 512 AND object_key !~ '[[:cntrl:]]'),
    CONSTRAINT ck_managed_media_observed_metadata
        CHECK (
            NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 0
            OR (NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 3
                AND observed_content_type = declared_content_type
                AND observed_size_bytes = declared_size_bytes
                AND CHAR_LENGTH(object_etag) BETWEEN 1 AND 255)
        ),
    CONSTRAINT ck_managed_media_ready_window
        CHECK (
            NUM_NONNULLS(ready_at, ready_expires_at) = 0
            OR (NUM_NONNULLS(ready_at, ready_expires_at) = 2 AND ready_expires_at > ready_at)
        ),
    CONSTRAINT ck_managed_media_cleanup_lease
        CHECK (
            (cleanup_lease_token IS NULL AND cleanup_lease_expires_at IS NULL)
            OR (cleanup_lease_token IS NOT NULL AND cleanup_lease_expires_at IS NOT NULL)
        ),
    CONSTRAINT ck_managed_media_cleanup_attempt_count CHECK (cleanup_attempt_count >= 0),
    CONSTRAINT ck_managed_media_timestamps
        CHECK (
            updated_at >= created_at
            AND upload_expires_at > created_at
            AND (cleanup_due_at IS NULL OR cleanup_due_at >= deletion_requested_at)
            AND (cleanup_next_attempt_at IS NULL OR cleanup_next_attempt_at >= cleanup_due_at)
            AND (deleted_at IS NULL OR deleted_at >= deletion_requested_at)
        ),
    CONSTRAINT ck_managed_media_lifecycle
        CHECK (
            (status = 'AWAITING_UPLOAD'
                AND NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 0
                AND NUM_NONNULLS(ready_at, ready_expires_at) = 0
                AND deletion_reason IS NULL
                AND deletion_requested_at IS NULL
                AND cleanup_due_at IS NULL
                AND cleanup_lease_token IS NULL
                AND cleanup_next_attempt_at IS NULL
                AND cleanup_last_error_code IS NULL
                AND deleted_at IS NULL)
            OR (status IN ('READY', 'ACTIVE')
                AND NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 3
                AND NUM_NONNULLS(ready_at, ready_expires_at) = 2
                AND deletion_reason IS NULL
                AND deletion_requested_at IS NULL
                AND cleanup_due_at IS NULL
                AND cleanup_lease_token IS NULL
                AND cleanup_next_attempt_at IS NULL
                AND cleanup_last_error_code IS NULL
                AND deleted_at IS NULL)
            OR (status = 'DELETION_PENDING'
                AND deletion_reason IS NOT NULL
                AND deletion_requested_at IS NOT NULL
                AND cleanup_due_at IS NOT NULL
                AND ((NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 0
                        AND NUM_NONNULLS(ready_at, ready_expires_at) = 0)
                    OR (NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 3
                        AND NUM_NONNULLS(ready_at, ready_expires_at) = 2))
                AND deleted_at IS NULL)
            OR (status = 'DELETED'
                AND deletion_reason IS NOT NULL
                AND deletion_requested_at IS NOT NULL
                AND cleanup_due_at IS NOT NULL
                AND cleanup_lease_token IS NULL
                AND cleanup_next_attempt_at IS NULL
                AND cleanup_last_error_code IS NULL
                AND ((NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 0
                        AND NUM_NONNULLS(ready_at, ready_expires_at) = 0)
                    OR (NUM_NONNULLS(observed_content_type, observed_size_bytes, object_etag) = 3
                        AND NUM_NONNULLS(ready_at, ready_expires_at) = 2))
                AND deleted_at IS NOT NULL)
        )
);

CREATE FUNCTION prevent_managed_media_immutable_facts_update()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF ROW(
        NEW.id,
        NEW.owner_account_id,
        NEW.purpose,
        NEW.object_key,
        NEW.declared_content_type,
        NEW.declared_size_bytes,
        NEW.upload_expires_at,
        NEW.created_at
    ) IS DISTINCT FROM ROW(
        OLD.id,
        OLD.owner_account_id,
        OLD.purpose,
        OLD.object_key,
        OLD.declared_content_type,
        OLD.declared_size_bytes,
        OLD.upload_expires_at,
        OLD.created_at
    ) THEN
        RAISE EXCEPTION 'managed_media reservation facts are immutable';
    END IF;

    IF OLD.observed_content_type IS NOT NULL
        AND ROW(NEW.observed_content_type, NEW.observed_size_bytes, NEW.object_etag)
            IS DISTINCT FROM ROW(OLD.observed_content_type, OLD.observed_size_bytes, OLD.object_etag) THEN
        RAISE EXCEPTION 'managed_media observed facts are immutable after confirmation';
    END IF;

    IF OLD.ready_at IS NOT NULL
        AND ROW(NEW.ready_at, NEW.ready_expires_at)
            IS DISTINCT FROM ROW(OLD.ready_at, OLD.ready_expires_at) THEN
        RAISE EXCEPTION 'managed_media ready window is immutable after confirmation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_managed_media_immutable_facts
    BEFORE UPDATE ON managed_media
    FOR EACH ROW
EXECUTE FUNCTION prevent_managed_media_immutable_facts_update();

CREATE INDEX idx_managed_media_owner_status
    ON managed_media (owner_account_id, status);

CREATE INDEX idx_managed_media_awaiting_upload_expiry
    ON managed_media (upload_expires_at, id)
    WHERE status = 'AWAITING_UPLOAD';

CREATE INDEX idx_managed_media_ready_expiry
    ON managed_media (ready_expires_at, id)
    WHERE status = 'READY';

CREATE INDEX idx_managed_media_cleanup_due
    ON managed_media (COALESCE(cleanup_next_attempt_at, cleanup_due_at), id)
    WHERE status = 'DELETION_PENDING';

ALTER TABLE profile
    ADD COLUMN avatar_media_id UUID,
    ADD COLUMN avatar_media_purpose VARCHAR(32)
        GENERATED ALWAYS AS (
            CASE WHEN avatar_media_id IS NULL THEN NULL ELSE 'AVATAR' END
        ) STORED,
    ADD CONSTRAINT uq_profile_avatar_media_id UNIQUE (avatar_media_id),
    ADD CONSTRAINT fk_profile_avatar_media
        FOREIGN KEY (avatar_media_id, avatar_media_purpose)
            REFERENCES managed_media (id, purpose) ON DELETE RESTRICT;
