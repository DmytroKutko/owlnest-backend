package dev.dkutko.owlnest.media.controller;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record MediaReferenceResponse(
        UUID mediaId,
        URI deliveryUrl
) {

    public MediaReferenceResponse {
        Objects.requireNonNull(mediaId, "mediaId must not be null");
        Objects.requireNonNull(deliveryUrl, "deliveryUrl must not be null");
    }

    public static MediaReferenceResponse from(UUID mediaId) {
        Objects.requireNonNull(mediaId, "mediaId must not be null");
        return new MediaReferenceResponse(
                mediaId,
                URI.create("/api/v1/media/" + mediaId + "/delivery")
        );
    }
}
