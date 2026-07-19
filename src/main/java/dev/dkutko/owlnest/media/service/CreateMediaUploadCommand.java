package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;

import java.util.Objects;

public record CreateMediaUploadCommand(
        ManagedMediaPurpose purpose,
        String contentType,
        long sizeBytes
) {

    public CreateMediaUploadCommand {
        Objects.requireNonNull(purpose, "purpose must not be null");
        purpose.validateDeclaredMetadata(contentType, sizeBytes);
    }
}
