package dev.dkutko.owlnest.media.domain;

import java.util.Objects;
import java.util.Set;

public enum ManagedMediaPurpose {
    AVATAR(
            Set.of("image/jpeg", "image/png", "image/webp"),
            10L * 1024 * 1024
    ),
    POST_IMAGE(
            Set.of("image/jpeg", "image/png", "image/webp"),
            20L * 1024 * 1024
    ),
    POST_VIDEO(
            Set.of("video/mp4", "video/quicktime"),
            250L * 1024 * 1024
    );

    private final Set<String> supportedContentTypes;
    private final long maximumSizeBytes;

    ManagedMediaPurpose(Set<String> supportedContentTypes, long maximumSizeBytes) {
        this.supportedContentTypes = supportedContentTypes;
        this.maximumSizeBytes = maximumSizeBytes;
    }

    public void validateDeclaredMetadata(String contentType, long sizeBytes) {
        Objects.requireNonNull(contentType, "declaredContentType must not be null");
        if (!supportedContentTypes.contains(contentType)) {
            throw new IllegalArgumentException("declaredContentType is not supported for purpose " + this);
        }
        if (sizeBytes < 1 || sizeBytes > maximumSizeBytes) {
            throw new IllegalArgumentException(
                    "declaredSizeBytes must be between 1 and " + maximumSizeBytes + " for purpose " + this
            );
        }
    }
}
