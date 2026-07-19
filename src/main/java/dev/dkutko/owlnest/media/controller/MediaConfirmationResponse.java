package dev.dkutko.owlnest.media.controller;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.service.ConfirmedMedia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record MediaConfirmationResponse(
        @Schema(example = "47c62a2c-ae5f-48d1-b05c-126cc1292392")
        UUID mediaId,

        @Schema(example = "AVATAR")
        ManagedMediaPurpose purpose,

        @Schema(example = "image/webp")
        String contentType,

        @Schema(example = "524288")
        long sizeBytes,

        Instant confirmedAt
) {

    static MediaConfirmationResponse from(ConfirmedMedia media) {
        return new MediaConfirmationResponse(
                media.mediaId(),
                media.purpose(),
                media.contentType(),
                media.sizeBytes(),
                media.confirmedAt()
        );
    }
}
