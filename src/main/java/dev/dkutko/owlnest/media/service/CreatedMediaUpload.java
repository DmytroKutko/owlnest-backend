package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreatedMediaUpload(
        UUID mediaId,
        ManagedMediaPurpose purpose,
        String contentType,
        long sizeBytes,
        URI uploadUrl,
        Map<String, String> requiredHeaders,
        Instant uploadExpiresAt
) {
}
