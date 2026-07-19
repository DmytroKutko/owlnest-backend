package dev.dkutko.owlnest.media.domain;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ManagedMediaTest {

    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");
    private static final Instant UPLOAD_EXPIRES_AT = NOW.plusSeconds(900);

    @Test
    void reservesAwaitingUploadWithImmutableReservationFacts() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "accounts/10000000-0000-0000-0000-000000000001/avatar/asset.webp",
                "image/webp",
                10_485_760,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertThat(media.getId()).isNotNull();
        assertThat(media.getOwnerAccountId()).isEqualTo(OWNER_ACCOUNT_ID);
        assertThat(media.getPurpose()).isEqualTo(ManagedMediaPurpose.AVATAR);
        assertThat(media.getObjectKey())
                .isEqualTo("accounts/10000000-0000-0000-0000-000000000001/avatar/asset.webp");
        assertThat(media.getDeclaredContentType()).isEqualTo("image/webp");
        assertThat(media.getDeclaredSizeBytes()).isEqualTo(10_485_760);
        assertThat(media.getUploadExpiresAt()).isEqualTo(UPLOAD_EXPIRES_AT);
        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.AWAITING_UPLOAD);
        assertThat(media.getCleanupAttemptCount()).isZero();
        assertThat(media.getCreatedAt()).isEqualTo(NOW);
        assertThat(media.getUpdatedAt()).isEqualTo(NOW);
    }

    @ParameterizedTest(name = "{0} accepts {1} at {2} bytes")
    @MethodSource("validMetadataBoundaries")
    void acceptsPurposeSpecificMimeAndInclusiveSizeBoundaries(
            ManagedMediaPurpose purpose,
            String contentType,
            long sizeBytes
    ) {
        ManagedMedia media = reserve(
                purpose,
                "managed/" + purpose.name().toLowerCase() + "/asset",
                contentType,
                sizeBytes,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertThat(media.getDeclaredContentType()).isEqualTo(contentType);
        assertThat(media.getDeclaredSizeBytes()).isEqualTo(sizeBytes);
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("invalidContentTypes")
    void rejectsContentTypeOutsideExactPurposeAllowlist(
            ManagedMediaPurpose purpose,
            String contentType
    ) {
        assertRejected(() -> reserve(
                purpose,
                "managed/asset",
                contentType,
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
    }

    @ParameterizedTest(name = "{0} rejects {1} bytes")
    @MethodSource("invalidSizeBoundaries")
    void rejectsSizeOutsideInclusivePurposeLimit(
            ManagedMediaPurpose purpose,
            long sizeBytes
    ) {
        String contentType = purpose == ManagedMediaPurpose.POST_VIDEO ? "video/mp4" : "image/jpeg";

        assertRejected(() -> reserve(
                purpose,
                "managed/asset",
                contentType,
                sizeBytes,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
    }

    @Test
    void acceptsObjectKeyAtExactLengthLimit() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_IMAGE,
                "a".repeat(512),
                "image/png",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertThat(media.getObjectKey()).hasSize(512);
    }

    @ParameterizedTest(name = "rejects invalid object key: {0}")
    @MethodSource("invalidObjectKeys")
    void rejectsBlankOversizedOrControlCharacterObjectKey(String scenario, String objectKey) {
        assertRejected(() -> reserve(
                ManagedMediaPurpose.POST_IMAGE,
                objectKey,
                "image/png",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
    }

    @Test
    void acceptsExpiryOneNanosecondAfterReservationTime() {
        Instant firstValidExpiry = NOW.plusNanos(1);

        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_VIDEO,
                "managed/video",
                "video/quicktime",
                1,
                firstValidExpiry,
                NOW
        );

        assertThat(media.getUploadExpiresAt()).isEqualTo(firstValidExpiry);
    }

    @Test
    void rejectsExpiryEqualToOrBeforeReservationTime() {
        assertRejected(() -> reserve(
                ManagedMediaPurpose.POST_VIDEO,
                "managed/video-equal",
                "video/mp4",
                1,
                NOW,
                NOW
        ));
        assertRejected(() -> reserve(
                ManagedMediaPurpose.POST_VIDEO,
                "managed/video-before",
                "video/mp4",
                1,
                NOW.minusNanos(1),
                NOW
        ));
    }

    @Test
    void rejectsNullReservationFacts() {
        assertRejected(() -> ManagedMedia.reserve(
                null,
                ManagedMediaPurpose.AVATAR,
                "managed/avatar",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
        assertRejected(() -> ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                null,
                "managed/avatar",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
        assertRejected(() -> ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                ManagedMediaPurpose.AVATAR,
                null,
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
        assertRejected(() -> ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                ManagedMediaPurpose.AVATAR,
                "managed/avatar",
                null,
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        ));
        assertRejected(() -> ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                ManagedMediaPurpose.AVATAR,
                "managed/avatar",
                "image/jpeg",
                1,
                null,
                NOW
        ));
        assertRejected(() -> ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                ManagedMediaPurpose.AVATAR,
                "managed/avatar",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                null
        ));
    }

    @Test
    void treatsUploadExpiryAsInclusiveAtExactInstant() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/avatar-expiry",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertThat(media.isUploadExpiredAt(UPLOAD_EXPIRES_AT.minusNanos(1))).isFalse();
        assertThat(media.isUploadExpiredAt(UPLOAD_EXPIRES_AT)).isTrue();
        assertThat(media.isUploadExpiredAt(UPLOAD_EXPIRES_AT.plusNanos(1))).isTrue();
    }

    @Test
    void confirmsAtLastValidUploadInstantWithFirstValidReadyExpiry() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_IMAGE,
                "managed/post-image-confirm",
                "image/webp",
                20_971_520,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant confirmedAt = UPLOAD_EXPIRES_AT.minusNanos(1);
        Instant readyExpiresAt = confirmedAt.plusNanos(1);

        media.confirm("image/webp", 20_971_520, "e".repeat(255), confirmedAt, readyExpiresAt);

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.READY);
        assertThat(media.isConfirmationEstablished()).isTrue();
        assertThat(media.getObservedContentType()).isEqualTo("image/webp");
        assertThat(media.getObservedSizeBytes()).isEqualTo(20_971_520);
        assertThat(media.getObjectEtag()).hasSize(255);
        assertThat(media.getReadyAt()).isEqualTo(confirmedAt);
        assertThat(media.getReadyExpiresAt()).isEqualTo(readyExpiresAt);
        assertThat(media.getUpdatedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void rejectsConfirmationAtExactUploadExpiryWithoutChangingReservation() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_VIDEO,
                "managed/expired-confirm",
                "video/mp4",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertRejected(() -> media.confirm(
                "video/mp4",
                1,
                "etag",
                UPLOAD_EXPIRES_AT,
                UPLOAD_EXPIRES_AT.plusSeconds(1)
        ));

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.AWAITING_UPLOAD);
        assertThat(media.getObservedContentType()).isNull();
        assertThat(media.getReadyAt()).isNull();
    }

    @ParameterizedTest(name = "rejects confirmation metadata: {0}")
    @MethodSource("invalidConfirmationMetadata")
    void rejectsMismatchedOrInvalidConfirmationMetadata(
            String scenario,
            String contentType,
            long sizeBytes,
            String etag
    ) {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/invalid-confirm-" + scenario,
                "image/png",
                10,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertRejected(() -> media.confirm(
                contentType,
                sizeBytes,
                etag,
                NOW.plusSeconds(1),
                NOW.plusSeconds(2)
        ));

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.AWAITING_UPLOAD);
    }

    @Test
    void rejectsReadyExpiryAtConfirmationInstantAndRepeatedDomainConfirmation() {
        ManagedMedia invalidWindow = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/invalid-ready-window",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant confirmedAt = NOW.plusSeconds(1);

        assertRejected(() -> invalidWindow.confirm(
                "image/jpeg",
                1,
                "etag",
                confirmedAt,
                confirmedAt
        ));

        ManagedMedia established = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/established-confirmation",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        established.confirm("image/jpeg", 1, "etag", confirmedAt, confirmedAt.plusNanos(1));

        assertRejected(() -> established.confirm(
                "image/jpeg",
                1,
                "etag",
                confirmedAt.plusNanos(1),
                confirmedAt.plusNanos(2)
        ));
    }

    @Test
    void cancelsAwaitingUploadAtInclusiveCleanupBoundary() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_VIDEO,
                "managed/cancel-awaiting",
                "video/quicktime",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant requestedAt = NOW.plusSeconds(1);

        media.cancel(requestedAt, requestedAt);

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.DELETION_PENDING);
        assertThat(media.getDeletionReason()).isEqualTo(ManagedMediaDeletionReason.USER_CANCELLED);
        assertThat(media.getDeletionRequestedAt()).isEqualTo(requestedAt);
        assertThat(media.getCleanupDueAt()).isEqualTo(requestedAt);
        assertThat(media.getCleanupNextAttemptAt()).isEqualTo(requestedAt);
        assertThat(media.getUpdatedAt()).isEqualTo(requestedAt);
    }

    @Test
    void cancelsReadyMediaWithoutChangingConfirmedFacts() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/cancel-ready",
                "image/webp",
                2,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant confirmedAt = NOW.plusSeconds(1);
        Instant readyExpiresAt = NOW.plusSeconds(10);
        media.confirm("image/webp", 2, "etag", confirmedAt, readyExpiresAt);

        media.cancel(NOW.plusSeconds(2), NOW.plusSeconds(3));

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.DELETION_PENDING);
        assertThat(media.getObservedContentType()).isEqualTo("image/webp");
        assertThat(media.getObservedSizeBytes()).isEqualTo(2);
        assertThat(media.getObjectEtag()).isEqualTo("etag");
        assertThat(media.getReadyAt()).isEqualTo(confirmedAt);
        assertThat(media.getReadyExpiresAt()).isEqualTo(readyExpiresAt);
    }

    @Test
    void rejectsCancellationBeforeCleanupBoundaryOrFromTerminalState() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.POST_IMAGE,
                "managed/cancel-invalid",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant requestedAt = NOW.plusSeconds(2);

        assertRejected(() -> media.cancel(requestedAt, requestedAt.minusNanos(1)));
        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.AWAITING_UPLOAD);

        media.cancel(requestedAt, requestedAt);
        assertRejected(() -> media.cancel(requestedAt.plusNanos(1), requestedAt.plusNanos(1)));
    }

    @Test
    void rejectsNullConfirmationAndCancellationFacts() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/null-transition-facts",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );

        assertRejected(() -> media.confirm(null, 1, "etag", NOW.plusSeconds(1), NOW.plusSeconds(2)));
        assertRejected(() -> media.confirm("image/jpeg", 1, null, NOW.plusSeconds(1), NOW.plusSeconds(2)));
        assertRejected(() -> media.confirm("image/jpeg", 1, "etag", null, NOW.plusSeconds(2)));
        assertRejected(() -> media.confirm("image/jpeg", 1, "etag", NOW.plusSeconds(1), null));
        assertRejected(() -> media.cancel(null, NOW.plusSeconds(2)));
        assertRejected(() -> media.cancel(NOW.plusSeconds(1), null));
    }

    @Test
    void expiresClaimsRetriesAndCompletesCleanupWithLeaseOwnership() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/cleanup-lifecycle",
                "image/jpeg",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        media.expireUpload(UPLOAD_EXPIRES_AT);
        UUID firstLease = UUID.randomUUID();
        Instant firstLeaseExpiry = UPLOAD_EXPIRES_AT.plusSeconds(120);
        media.claimCleanup(firstLease, UPLOAD_EXPIRES_AT, firstLeaseExpiry);

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.DELETION_PENDING);
        assertThat(media.getDeletionReason()).isEqualTo(ManagedMediaDeletionReason.UPLOAD_EXPIRED);
        assertThat(media.getCleanupAttemptCount()).isOne();
        assertRejected(() -> media.completeCleanup(UUID.randomUUID(), UPLOAD_EXPIRES_AT.plusSeconds(1)));

        Instant retryAt = UPLOAD_EXPIRES_AT.plusSeconds(60);
        media.retryCleanup(firstLease, UPLOAD_EXPIRES_AT.plusSeconds(1), retryAt, "STORAGE_UNAVAILABLE");
        assertThat(media.getCleanupLeaseToken()).isNull();
        assertThat(media.getCleanupNextAttemptAt()).isEqualTo(retryAt);
        assertThat(media.getCleanupLastErrorCode()).isEqualTo("STORAGE_UNAVAILABLE");

        UUID secondLease = UUID.randomUUID();
        media.claimCleanup(secondLease, retryAt, retryAt.plusSeconds(120));
        media.completeCleanup(secondLease, retryAt.plusSeconds(1));

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.DELETED);
        assertThat(media.getCleanupAttemptCount()).isEqualTo(2);
        assertThat(media.getDeletedAt()).isEqualTo(retryAt.plusSeconds(1));
        assertThat(media.getCleanupLeaseToken()).isNull();
        assertThat(media.getCleanupNextAttemptAt()).isNull();
        assertThat(media.getCleanupLastErrorCode()).isNull();
    }

    @Test
    void expiresReadyMediaOnlyAtInclusiveReadyDeadline() {
        ManagedMedia media = reserve(
                ManagedMediaPurpose.AVATAR,
                "managed/ready-cleanup",
                "image/webp",
                1,
                UPLOAD_EXPIRES_AT,
                NOW
        );
        Instant readyAt = NOW.plusSeconds(1);
        Instant readyExpiresAt = NOW.plusSeconds(2);
        media.confirm("image/webp", 1, "etag", readyAt, readyExpiresAt);

        assertRejected(() -> media.expireReady(readyExpiresAt.minusNanos(1)));
        media.expireReady(readyExpiresAt);

        assertThat(media.getStatus()).isEqualTo(ManagedMediaStatus.DELETION_PENDING);
        assertThat(media.getDeletionReason()).isEqualTo(ManagedMediaDeletionReason.READY_EXPIRED);
        assertThat(media.getCleanupDueAt()).isEqualTo(readyExpiresAt);
    }

    private static ManagedMedia reserve(
            ManagedMediaPurpose purpose,
            String objectKey,
            String contentType,
            long sizeBytes,
            Instant uploadExpiresAt,
            Instant now
    ) {
        return ManagedMedia.reserve(
                OWNER_ACCOUNT_ID,
                purpose,
                objectKey,
                contentType,
                sizeBytes,
                uploadExpiresAt,
                now
        );
    }

    private static void assertRejected(ThrowingCallable reservation) {
        assertThatThrownBy(reservation).isInstanceOf(RuntimeException.class);
    }

    private static Stream<Arguments> validMetadataBoundaries() {
        return Stream.of(
                arguments(ManagedMediaPurpose.AVATAR, "image/jpeg", 1L),
                arguments(ManagedMediaPurpose.AVATAR, "image/png", 10_485_760L),
                arguments(ManagedMediaPurpose.AVATAR, "image/webp", 1L),
                arguments(ManagedMediaPurpose.POST_IMAGE, "image/jpeg", 1L),
                arguments(ManagedMediaPurpose.POST_IMAGE, "image/png", 20_971_520L),
                arguments(ManagedMediaPurpose.POST_IMAGE, "image/webp", 1L),
                arguments(ManagedMediaPurpose.POST_VIDEO, "video/mp4", 1L),
                arguments(ManagedMediaPurpose.POST_VIDEO, "video/quicktime", 262_144_000L)
        );
    }

    private static Stream<Arguments> invalidContentTypes() {
        return Stream.of(
                arguments(ManagedMediaPurpose.AVATAR, "video/mp4"),
                arguments(ManagedMediaPurpose.AVATAR, "image/gif"),
                arguments(ManagedMediaPurpose.POST_IMAGE, "video/quicktime"),
                arguments(ManagedMediaPurpose.POST_VIDEO, "image/jpeg"),
                arguments(ManagedMediaPurpose.POST_VIDEO, "VIDEO/MP4"),
                arguments(ManagedMediaPurpose.POST_IMAGE, "image/png; charset=binary"),
                arguments(ManagedMediaPurpose.POST_IMAGE, "")
        );
    }

    private static Stream<Arguments> invalidSizeBoundaries() {
        return Stream.of(
                arguments(ManagedMediaPurpose.AVATAR, 0L),
                arguments(ManagedMediaPurpose.AVATAR, 10_485_761L),
                arguments(ManagedMediaPurpose.POST_IMAGE, 0L),
                arguments(ManagedMediaPurpose.POST_IMAGE, 20_971_521L),
                arguments(ManagedMediaPurpose.POST_VIDEO, 0L),
                arguments(ManagedMediaPurpose.POST_VIDEO, 262_144_001L)
        );
    }

    private static Stream<Arguments> invalidObjectKeys() {
        return Stream.of(
                arguments("empty", ""),
                arguments("blank", "   "),
                arguments("513 characters", "a".repeat(513)),
                arguments("line feed", "managed/\nasset"),
                arguments("NUL", "managed/\0asset")
        );
    }

    private static Stream<Arguments> invalidConfirmationMetadata() {
        return Stream.of(
                arguments("content-type", "image/jpeg", 10L, "etag"),
                arguments("size", "image/png", 11L, "etag"),
                arguments("empty-etag", "image/png", 10L, ""),
                arguments("blank-etag", "image/png", 10L, "   "),
                arguments("long-etag", "image/png", 10L, "e".repeat(256))
        );
    }
}
