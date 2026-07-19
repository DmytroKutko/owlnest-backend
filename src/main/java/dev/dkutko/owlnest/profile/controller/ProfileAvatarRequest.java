package dev.dkutko.owlnest.profile.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProfileAvatarRequest(
        @NotNull
        @Schema(example = "47c62a2c-ae5f-48d1-b05c-126cc1292392")
        UUID mediaId
) {
}
