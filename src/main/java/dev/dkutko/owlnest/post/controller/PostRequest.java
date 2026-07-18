package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.domain.PostMedia;
import dev.dkutko.owlnest.post.domain.PostMediaType;
import dev.dkutko.owlnest.post.domain.PostType;
import dev.dkutko.owlnest.post.service.PostWriteCommand;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.util.List;

public record PostRequest(
        @Schema(
                types = {"string", "null"},
                maxLength = 200,
                description = "Maximum length in Unicode code points after trimming"
        )
        String title,
        @NotBlank
        @Schema(minLength = 1, maxLength = 20_000)
        String description,
        PostType postType,
        @ArraySchema(
                maxItems = 5,
                schema = @Schema(
                        minLength = 1,
                        maxLength = 50,
                        description = "Maximum length in Unicode code points after trimming"
                )
        )
        @Size(max = 5)
        List<@NotBlank String> labels,
        @Size(max = 10)
        List<@NotNull @Valid Media> media
) {

    PostWriteCommand toCommand() {
        List<PostMedia> mediaValues = media == null
                ? List.of()
                : media.stream().map(Media::toDomain).toList();
        return new PostWriteCommand(postType, title, description, labels, mediaValues);
    }

    public record Media(
            @NotNull
            PostMediaType type,
            @NotBlank
            @Schema(minLength = 1, maxLength = 2_048)
            String url
    ) {

        PostMedia toDomain() {
            return new PostMedia(type, URI.create(url));
        }
    }
}
