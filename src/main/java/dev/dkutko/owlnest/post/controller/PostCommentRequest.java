package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.CreatePostCommentCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PostCommentRequest(
        @NotBlank
        @Schema(
                minLength = 1,
                maxLength = 5_000,
                description = "Exact comment text; maximum length is measured in Unicode code points"
        )
        String text
) {

    CreatePostCommentCommand toCommand() {
        return new CreatePostCommentCommand(text);
    }
}
