package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.domain.ManagedMediaStatus;
import dev.dkutko.owlnest.media.repository.ManagedMediaRepository;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class ManagedMediaTransactionService {

    private static final Duration READY_RETENTION = Duration.ofHours(24);
    private static final Duration CANCELLATION_RETENTION = Duration.ofHours(24);
    private static final long MAX_OWNER_OBJECTS = 10;
    private static final long MAX_OWNER_DECLARED_BYTES = 100L * 1024 * 1024;

    private final ManagedMediaRepository mediaRepository;

    public ManagedMediaTransactionService(ManagedMediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public Reservation reserve(
            UUID ownerAccountId,
            CreateMediaUploadCommand command,
            Duration uploadTtl
    ) {
        mediaRepository.lockOwnerReservationQuota(ownerAccountId);
        ManagedMediaRepository.OwnerStorageUsage usage = mediaRepository.getOwnerStorageUsage(ownerAccountId);
        if (usage.objectCount() >= MAX_OWNER_OBJECTS
                || usage.declaredBytes() > MAX_OWNER_DECLARED_BYTES - command.sizeBytes()) {
            throw new MediaStorageQuotaExceededException();
        }
        Instant now = Instant.now();
        Instant uploadExpiresAt = now.plus(uploadTtl);
        String objectKey = generateObjectKey(command.purpose());
        ManagedMedia media = ManagedMedia.reserve(
                ownerAccountId,
                command.purpose(),
                objectKey,
                command.contentType(),
                command.sizeBytes(),
                uploadExpiresAt,
                now
        );
        mediaRepository.save(media);
        return new Reservation(
                media.getId(),
                media.getPurpose(),
                media.getDeclaredContentType(),
                media.getDeclaredSizeBytes(),
                media.getObjectKey(),
                media.getUploadExpiresAt()
        );
    }

    @Transactional(readOnly = true)
    public ConfirmationPreflight preflightConfirmation(UUID mediaId, UUID ownerAccountId) {
        ManagedMedia media = mediaRepository.findOwnedById(mediaId, ownerAccountId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.isConfirmationEstablished()) {
            return ConfirmationPreflight.established(toConfirmedMedia(media));
        }
        if (media.getStatus() != ManagedMediaStatus.AWAITING_UPLOAD) {
            throw new MediaConfirmationConflictException();
        }
        if (media.isUploadExpiredAt(Instant.now())) {
            throw new MediaUploadExpiredException();
        }
        return ConfirmationPreflight.requiresInspection(media.getObjectKey());
    }

    @Transactional
    public ConfirmedMedia confirm(
            UUID mediaId,
            UUID ownerAccountId,
            MediaObjectStorage.ObjectMetadata observedMetadata
    ) {
        ManagedMedia media = mediaRepository.findOwnedByIdForUpdate(mediaId, ownerAccountId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.isConfirmationEstablished()) {
            return toConfirmedMedia(media);
        }
        if (media.getStatus() != ManagedMediaStatus.AWAITING_UPLOAD) {
            throw new MediaConfirmationConflictException();
        }

        Instant now = Instant.now();
        if (media.isUploadExpiredAt(now)) {
            throw new MediaUploadExpiredException();
        }
        if (!metadataMatches(media, observedMetadata)) {
            throw new MediaUploadMismatchException();
        }

        media.confirm(
                observedMetadata.contentType(),
                observedMetadata.contentLength(),
                observedMetadata.etag(),
                now,
                now.plus(READY_RETENTION)
        );
        mediaRepository.save(media);
        return toConfirmedMedia(media);
    }

    @Transactional
    public void cancel(UUID mediaId, UUID ownerAccountId) {
        ManagedMedia media = mediaRepository.findOwnedByIdForUpdate(mediaId, ownerAccountId)
                .orElseThrow(MediaNotFoundException::new);
        if (media.getStatus() == ManagedMediaStatus.ACTIVE) {
            throw new MediaInUseException();
        }
        if (media.getStatus() != ManagedMediaStatus.AWAITING_UPLOAD
                && media.getStatus() != ManagedMediaStatus.READY) {
            throw new MediaNotFoundException();
        }

        Instant now = Instant.now();
        media.cancel(now, now.plus(CANCELLATION_RETENTION));
        mediaRepository.save(media);
    }

    private static boolean metadataMatches(
            ManagedMedia media,
            MediaObjectStorage.ObjectMetadata observedMetadata
    ) {
        return media.getDeclaredContentType().equals(observedMetadata.contentType())
                && media.getDeclaredSizeBytes() == observedMetadata.contentLength()
                && observedMetadata.etag() != null
                && !observedMetadata.etag().isBlank()
                && observedMetadata.etag().length() <= 255;
    }

    private static ConfirmedMedia toConfirmedMedia(ManagedMedia media) {
        return new ConfirmedMedia(
                media.getId(),
                media.getPurpose(),
                media.getDeclaredContentType(),
                media.getDeclaredSizeBytes(),
                media.getReadyAt()
        );
    }

    private static String generateObjectKey(ManagedMediaPurpose purpose) {
        String prefix = switch (purpose) {
            case AVATAR -> "managed/v1/avatars/";
            case POST_IMAGE -> "managed/v1/posts/";
            case POST_VIDEO -> throw new IllegalArgumentException("Post video uploads are not enabled");
        };
        return prefix + UUID.randomUUID();
    }

    record Reservation(
            UUID mediaId,
            ManagedMediaPurpose purpose,
            String contentType,
            long sizeBytes,
            String objectKey,
            Instant uploadExpiresAt
    ) {
    }

    record ConfirmationPreflight(
            String objectKey,
            ConfirmedMedia establishedConfirmation
    ) {

        static ConfirmationPreflight established(ConfirmedMedia confirmedMedia) {
            return new ConfirmationPreflight(null, confirmedMedia);
        }

        static ConfirmationPreflight requiresInspection(String objectKey) {
            return new ConfirmationPreflight(objectKey, null);
        }

        public boolean isEstablished() {
            return establishedConfirmation != null;
        }
    }
}
