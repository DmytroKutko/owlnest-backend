package dev.dkutko.owlnest;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class ManagedMediaTestFixtures {

    private static final Duration DEFAULT_READY_WINDOW = Duration.ofHours(24);

    private final JdbcTemplate jdbcTemplate;

    ManagedMediaTestFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    UUID insertAwaitingAvatar(UUID ownerAccountId) {
        UUID mediaId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(5);
        jdbcTemplate.update(
                """
                        INSERT INTO managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            upload_expires_at, cleanup_attempt_count,
                            created_at, updated_at
                        )
                        VALUES (?, ?, 'AVATAR', ?, 'image/webp', 42, 'AWAITING_UPLOAD', ?, 0, ?, ?)
                        """,
                mediaId,
                ownerAccountId,
                objectKey(ownerAccountId, mediaId),
                timestamp(createdAt.plus(Duration.ofMinutes(15))),
                timestamp(createdAt),
                timestamp(createdAt)
        );
        return mediaId;
    }

    UUID insertReadyAvatar(UUID ownerAccountId) {
        return insertReady(ownerAccountId, "AVATAR", Instant.now().plus(DEFAULT_READY_WINDOW));
    }

    UUID insertExpiredReadyAvatar(UUID ownerAccountId) {
        return insertReady(ownerAccountId, "AVATAR", Instant.now().minusMillis(1));
    }

    UUID insertReadyPostImage(UUID ownerAccountId) {
        return insertReady(ownerAccountId, "POST_IMAGE", Instant.now().plus(DEFAULT_READY_WINDOW));
    }

    UUID insertDeletionPendingAvatar(UUID ownerAccountId) {
        UUID mediaId = insertReadyAvatar(ownerAccountId);
        Instant requestedAt = Instant.now().minusSeconds(1);
        Instant cleanupAt = requestedAt.plus(Duration.ofHours(24));
        jdbcTemplate.update(
                """
                        UPDATE managed_media
                        SET status = 'DELETION_PENDING',
                            deletion_reason = 'USER_CANCELLED',
                            deletion_requested_at = ?,
                            cleanup_due_at = ?,
                            cleanup_next_attempt_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                timestamp(requestedAt),
                timestamp(cleanupAt),
                timestamp(cleanupAt),
                timestamp(requestedAt),
                mediaId
        );
        return mediaId;
    }

    UUID insertDueDeletionPendingAvatar(UUID ownerAccountId) {
        UUID mediaId = insertReadyAvatar(ownerAccountId);
        Instant requestedAt = Instant.now().minusSeconds(2);
        Instant cleanupAt = Instant.now().minusSeconds(1);
        jdbcTemplate.update(
                """
                        UPDATE managed_media
                        SET status = 'DELETION_PENDING',
                            deletion_reason = 'USER_CANCELLED',
                            deletion_requested_at = ?,
                            cleanup_due_at = ?,
                            cleanup_next_attempt_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                timestamp(requestedAt),
                timestamp(cleanupAt),
                timestamp(cleanupAt),
                timestamp(requestedAt),
                mediaId
        );
        return mediaId;
    }

    UUID insertDeletedAvatar(UUID ownerAccountId) {
        UUID mediaId = insertReadyAvatar(ownerAccountId);
        Instant requestedAt = Instant.now().minusSeconds(2);
        Instant deletedAt = requestedAt.plusSeconds(1);
        jdbcTemplate.update(
                """
                        UPDATE managed_media
                        SET status = 'DELETED',
                            deletion_reason = 'USER_CANCELLED',
                            deletion_requested_at = ?,
                            cleanup_due_at = ?,
                            cleanup_attempt_count = 1,
                            cleanup_next_attempt_at = NULL,
                            deleted_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                timestamp(requestedAt),
                timestamp(requestedAt),
                timestamp(deletedAt),
                timestamp(deletedAt),
                mediaId
        );
        return mediaId;
    }

    void activateAvatar(UUID accountId, UUID mediaId) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE managed_media SET status = 'ACTIVE', updated_at = ? WHERE id = ?",
                timestamp(now),
                mediaId
        );
        jdbcTemplate.update(
                "UPDATE profile SET avatar_media_id = ?, updated_at = ? WHERE account_id = ?",
                mediaId,
                timestamp(now),
                accountId
        );
    }

    void detachAvatar(UUID accountId, UUID mediaId, String reason) {
        Instant requestedAt = Instant.now();
        Instant cleanupAt = requestedAt.plus(Duration.ofHours(24));
        jdbcTemplate.update(
                "UPDATE profile SET avatar_media_id = NULL, updated_at = ? WHERE account_id = ?",
                timestamp(requestedAt),
                accountId
        );
        jdbcTemplate.update(
                """
                        UPDATE managed_media
                        SET status = 'DELETION_PENDING',
                            deletion_reason = ?,
                            deletion_requested_at = ?,
                            cleanup_due_at = ?,
                            cleanup_next_attempt_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                reason,
                timestamp(requestedAt),
                timestamp(cleanupAt),
                timestamp(cleanupAt),
                timestamp(requestedAt),
                mediaId
        );
    }

    UUID currentAvatarId(UUID accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT avatar_media_id FROM profile WHERE account_id = ?",
                UUID.class,
                accountId
        );
    }

    Map<String, Object> mediaRow(UUID mediaId) {
        return jdbcTemplate.queryForMap(
                """
                        SELECT id, owner_account_id, purpose, object_key, status,
                               ready_at, ready_expires_at, deletion_reason,
                               deletion_requested_at, cleanup_due_at,
                               cleanup_next_attempt_at, deleted_at
                        FROM managed_media
                        WHERE id = ?
                        """,
                mediaId
        );
    }

    private UUID insertReady(UUID ownerAccountId, String purpose, Instant readyExpiresAt) {
        UUID mediaId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(120);
        Instant readyAt = Instant.now().minusSeconds(60);
        String contentType = purpose.equals("POST_VIDEO") ? "video/mp4" : "image/webp";
        jdbcTemplate.update(
                """
                        INSERT INTO managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            observed_content_type, observed_size_bytes, object_etag,
                            upload_expires_at, ready_at, ready_expires_at,
                            cleanup_attempt_count, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, 42, 'READY', ?, 42, ?, ?, ?, ?, 0, ?, ?)
                        """,
                mediaId,
                ownerAccountId,
                purpose,
                objectKey(ownerAccountId, mediaId),
                contentType,
                contentType,
                "etag-" + mediaId,
                timestamp(createdAt.plus(Duration.ofMinutes(15))),
                timestamp(readyAt),
                timestamp(readyExpiresAt),
                timestamp(createdAt),
                timestamp(readyAt)
        );
        return mediaId;
    }

    private static String objectKey(UUID ownerAccountId, UUID mediaId) {
        return "accounts/" + ownerAccountId + "/media/" + mediaId;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
