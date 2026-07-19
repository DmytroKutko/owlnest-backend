package dev.dkutko.owlnest.media.controller;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.service.CreateMediaUploadCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MediaUploadRequest(
        @NotNull
        @Schema(
                description = "Implemented OwlNest use for the object",
                example = "POST_IMAGE",
                allowableValues = {"AVATAR", "POST_IMAGE"}
        )
        ManagedMediaPurpose purpose,

        @NotBlank
        @Size(min = 1, max = 127)
        @Schema(description = "Exact MIME type that Flutter will upload", example = "image/webp")
        String contentType,

        @NotNull
        @Positive
        @Schema(description = "Exact object size in bytes", example = "524288", minimum = "1")
        Long sizeBytes
) {

    CreateMediaUploadCommand toCommand() {
        return new CreateMediaUploadCommand(purpose, contentType, sizeBytes);
    }
}
