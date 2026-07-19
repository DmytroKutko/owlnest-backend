package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;

import java.time.Instant;
import java.util.UUID;

public record ConfirmedMedia(
        UUID mediaId,
        ManagedMediaPurpose purpose,
        String contentType,
        long sizeBytes,
        Instant confirmedAt
) {
}
