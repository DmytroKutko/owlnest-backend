package dev.dkutko.owlnest.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "managed_media",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_managed_media_object_key", columnNames = "object_key"),
                @UniqueConstraint(name = "uq_managed_media_id_purpose", columnNames = {"id", "purpose"})
        }
)
public class ManagedMedia {

    private static final int MAX_OBJECT_KEY_CODE_POINTS = 512;
    @Id
    private UUID id;

    @Column(name = "owner_account_id", nullable = false, updatable = false)
    private UUID ownerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ManagedMediaPurpose purpose;

    @Column(name = "object_key", nullable = false, length = 512, updatable = false)
    private String objectKey;

    @Column(name = "declared_content_type", nullable = false, length = 127, updatable = false)
    private String declaredContentType;

    @Column(name = "declared_size_bytes", nullable = false, updatable = false)
    private long declaredSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ManagedMediaStatus status;

    @Column(name = "observed_content_type", length = 127)
    private String observedContentType;

    @Column(name = "observed_size_bytes")
    private Long observedSizeBytes;

    @Column(name = "object_etag", length = 255)
    private String objectEtag;

    @Column(name = "upload_expires_at", nullable = false, updatable = false)
    private Instant uploadExpiresAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "ready_expires_at")
    private Instant readyExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_reason", length = 32)
    private ManagedMediaDeletionReason deletionReason;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "cleanup_due_at")
    private Instant cleanupDueAt;

    @Column(name = "cleanup_lease_token")
    private UUID cleanupLeaseToken;

    @Column(name = "cleanup_lease_expires_at")
    private Instant cleanupLeaseExpiresAt;

    @Column(name = "cleanup_attempt_count", nullable = false)
    private int cleanupAttemptCount;

    @Column(name = "cleanup_next_attempt_at")
    private Instant cleanupNextAttemptAt;

    @Column(name = "cleanup_last_error_code", length = 64)
    private String cleanupLastErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ManagedMedia() {
    }

    private ManagedMedia(
            UUID id,
            UUID ownerAccountId,
            ManagedMediaPurpose purpose,
            String objectKey,
            String declaredContentType,
            long declaredSizeBytes,
            Instant uploadExpiresAt,
            Instant now
    ) {
        this.id = id;
        this.ownerAccountId = ownerAccountId;
        this.purpose = purpose;
        this.objectKey = objectKey;
        this.declaredContentType = declaredContentType;
        this.declaredSizeBytes = declaredSizeBytes;
        this.status = ManagedMediaStatus.AWAITING_UPLOAD;
        this.uploadExpiresAt = uploadExpiresAt;
        this.cleanupAttemptCount = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ManagedMedia reserve(
            UUID ownerAccountId,
            ManagedMediaPurpose purpose,
            String objectKey,
            String declaredContentType,
            long declaredSizeBytes,
            Instant uploadExpiresAt,
            Instant now
    ) {
        Objects.requireNonNull(ownerAccountId, "ownerAccountId must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        requireValidObjectKey(objectKey);
        purpose.validateDeclaredMetadata(declaredContentType, declaredSizeBytes);
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(uploadExpiresAt, "uploadExpiresAt must not be null");
        if (!uploadExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("uploadExpiresAt must be after now");
        }
        return new ManagedMedia(
                UUID.randomUUID(),
                ownerAccountId,
                purpose,
                objectKey,
                declaredContentType,
                declaredSizeBytes,
                uploadExpiresAt,
                now
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerAccountId() {
        return ownerAccountId;
    }

    public ManagedMediaPurpose getPurpose() {
        return purpose;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getDeclaredContentType() {
        return declaredContentType;
    }

    public long getDeclaredSizeBytes() {
        return declaredSizeBytes;
    }

    public ManagedMediaStatus getStatus() {
        return status;
    }

    public boolean isOwnedBy(UUID accountId) {
        return ownerAccountId.equals(accountId);
    }

    public boolean isUploadExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return !instant.isBefore(uploadExpiresAt);
    }

    public boolean isConfirmationEstablished() {
        return status == ManagedMediaStatus.READY || status == ManagedMediaStatus.ACTIVE;
    }

    public boolean isReadyExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return readyExpiresAt == null || !instant.isBefore(readyExpiresAt);
    }

    public void confirm(
            String contentType,
            long sizeBytes,
            String etag,
            Instant confirmedAt,
            Instant expiresAt
    ) {
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(etag, "etag must not be null");
        Objects.requireNonNull(confirmedAt, "confirmedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (status != ManagedMediaStatus.AWAITING_UPLOAD) {
            throw new IllegalStateException("media must be awaiting upload before confirmation");
        }
        if (isUploadExpiredAt(confirmedAt)) {
            throw new IllegalStateException("media upload has expired");
        }
        if (!declaredContentType.equals(contentType) || declaredSizeBytes != sizeBytes) {
            throw new IllegalArgumentException("observed metadata does not match the reservation");
        }
        if (etag.isBlank() || etag.length() > 255) {
            throw new IllegalArgumentException("etag must contain between 1 and 255 characters");
        }
        if (!expiresAt.isAfter(confirmedAt)) {
            throw new IllegalArgumentException("ready expiry must be after confirmation");
        }

        observedContentType = contentType;
        observedSizeBytes = sizeBytes;
        objectEtag = etag;
        readyAt = confirmedAt;
        readyExpiresAt = expiresAt;
        status = ManagedMediaStatus.READY;
        updatedAt = confirmedAt;
    }

    public void cancel(Instant requestedAt, Instant cleanupDueAt) {
        if (status != ManagedMediaStatus.AWAITING_UPLOAD && status != ManagedMediaStatus.READY) {
            throw new IllegalStateException("only pending or ready media can be cancelled");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.USER_CANCELLED, requestedAt, cleanupDueAt);
    }

    public void activateAvatar(Instant activatedAt) {
        Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        if (purpose != ManagedMediaPurpose.AVATAR) {
            throw new IllegalStateException("only avatar media can be activated as an avatar");
        }
        if (status != ManagedMediaStatus.READY) {
            throw new IllegalStateException("media must be ready before avatar activation");
        }
        if (isReadyExpiredAt(activatedAt)) {
            throw new IllegalStateException("ready media has expired");
        }
        status = ManagedMediaStatus.ACTIVE;
        updatedAt = activatedAt;
    }

    public void activatePostImage(Instant activatedAt) {
        Objects.requireNonNull(activatedAt, "activatedAt must not be null");
        if (purpose != ManagedMediaPurpose.POST_IMAGE) {
            throw new IllegalStateException("only post image media can be activated as a post image");
        }
        if (status != ManagedMediaStatus.READY || isReadyExpiredAt(activatedAt)) {
            throw new IllegalStateException("media must be unexpired and ready before post activation");
        }
        status = ManagedMediaStatus.ACTIVE;
        updatedAt = activatedAt;
    }

    public void detach(Instant requestedAt, Instant cleanupDueAt) {
        if (status != ManagedMediaStatus.ACTIVE) {
            throw new IllegalStateException("only active media can be detached");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.DETACHED, requestedAt, cleanupDueAt);
    }

    public void supersede(Instant requestedAt, Instant cleanupDueAt) {
        if (status != ManagedMediaStatus.ACTIVE) {
            throw new IllegalStateException("only active media can be superseded");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.SUPERSEDED, requestedAt, cleanupDueAt);
    }

    public void removeByUser(Instant requestedAt, Instant cleanupDueAt) {
        if (status != ManagedMediaStatus.ACTIVE) {
            throw new IllegalStateException("only active media can be removed");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.USER_REMOVED, requestedAt, cleanupDueAt);
    }

    public void expireUpload(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != ManagedMediaStatus.AWAITING_UPLOAD || !isUploadExpiredAt(now)) {
            throw new IllegalStateException("only expired awaiting media can be expired");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.UPLOAD_EXPIRED, now, now);
    }

    public void expireReady(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (status != ManagedMediaStatus.READY || !isReadyExpiredAt(now)) {
            throw new IllegalStateException("only expired ready media can be expired");
        }
        transitionToDeletionPending(ManagedMediaDeletionReason.READY_EXPIRED, now, now);
    }

    public void claimCleanup(UUID leaseToken, Instant now, Instant leaseExpiresAt) {
        Objects.requireNonNull(leaseToken, "leaseToken must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt must not be null");
        if (status != ManagedMediaStatus.DELETION_PENDING
                || cleanupNextAttemptAt.isAfter(now)
                || cleanupLeaseExpiresAt != null && cleanupLeaseExpiresAt.isAfter(now)
                || !leaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("media cleanup is not claimable");
        }
        cleanupLeaseToken = leaseToken;
        cleanupLeaseExpiresAt = leaseExpiresAt;
        cleanupAttemptCount++;
        updatedAt = now;
    }

    public void completeCleanup(UUID leaseToken, Instant now) {
        requireActiveCleanupLease(leaseToken, now);
        status = ManagedMediaStatus.DELETED;
        cleanupLeaseToken = null;
        cleanupLeaseExpiresAt = null;
        cleanupNextAttemptAt = null;
        cleanupLastErrorCode = null;
        deletedAt = now;
        updatedAt = now;
    }

    public void retryCleanup(UUID leaseToken, Instant now, Instant nextAttemptAt, String errorCode) {
        requireActiveCleanupLease(leaseToken, now);
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt must not be null");
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        if (!nextAttemptAt.isAfter(now) || errorCode.isBlank() || errorCode.length() > 64) {
            throw new IllegalArgumentException("cleanup retry must have a future attempt and safe error code");
        }
        cleanupLeaseToken = null;
        cleanupLeaseExpiresAt = null;
        cleanupNextAttemptAt = nextAttemptAt;
        cleanupLastErrorCode = errorCode;
        updatedAt = now;
    }

    public String getObservedContentType() {
        return observedContentType;
    }

    public Long getObservedSizeBytes() {
        return observedSizeBytes;
    }

    public String getObjectEtag() {
        return objectEtag;
    }

    public Instant getUploadExpiresAt() {
        return uploadExpiresAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getReadyExpiresAt() {
        return readyExpiresAt;
    }

    public ManagedMediaDeletionReason getDeletionReason() {
        return deletionReason;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public Instant getCleanupDueAt() {
        return cleanupDueAt;
    }

    public UUID getCleanupLeaseToken() {
        return cleanupLeaseToken;
    }

    public Instant getCleanupLeaseExpiresAt() {
        return cleanupLeaseExpiresAt;
    }

    public int getCleanupAttemptCount() {
        return cleanupAttemptCount;
    }

    public Instant getCleanupNextAttemptAt() {
        return cleanupNextAttemptAt;
    }

    public String getCleanupLastErrorCode() {
        return cleanupLastErrorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    private static void requireValidObjectKey(String objectKey) {
        Objects.requireNonNull(objectKey, "objectKey must not be null");
        if (objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (objectKey.codePointCount(0, objectKey.length()) > MAX_OBJECT_KEY_CODE_POINTS) {
            throw new IllegalArgumentException("objectKey must not exceed 512 Unicode code points");
        }
        if (objectKey.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("objectKey must not contain control characters");
        }
    }

    private void requireActiveCleanupLease(UUID leaseToken, Instant now) {
        Objects.requireNonNull(leaseToken, "leaseToken must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (status != ManagedMediaStatus.DELETION_PENDING
                || !leaseToken.equals(cleanupLeaseToken)
                || cleanupLeaseExpiresAt == null
                || !cleanupLeaseExpiresAt.isAfter(now)) {
            throw new IllegalStateException("cleanup lease is no longer active");
        }
    }

    private void transitionToDeletionPending(
            ManagedMediaDeletionReason reason,
            Instant requestedAt,
            Instant cleanupDueAt
    ) {
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(cleanupDueAt, "cleanupDueAt must not be null");
        if (cleanupDueAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("cleanup due time must not precede deletion request");
        }

        status = ManagedMediaStatus.DELETION_PENDING;
        deletionReason = reason;
        deletionRequestedAt = requestedAt;
        this.cleanupDueAt = cleanupDueAt;
        cleanupNextAttemptAt = cleanupDueAt;
        updatedAt = requestedAt;
    }

}
