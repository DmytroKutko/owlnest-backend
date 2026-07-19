package dev.dkutko.owlnest.post.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dkutko.owlnest.media.controller.MediaReferenceResponse;
import dev.dkutko.owlnest.post.domain.PostMediaType;
import dev.dkutko.owlnest.post.domain.PostType;
import dev.dkutko.owlnest.post.service.PostCard;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        @Schema(types = {"string", "null"}, maxLength = 200)
        String title,
        @Schema(minLength = 1, maxLength = 20_000)
        String description,
        Author author,
        PostType postType,
        @ArraySchema(maxItems = 5, schema = @Schema(minLength = 1, maxLength = 50))
        List<String> labels,
        List<Media> media,
        Counters counters,
        ViewerState viewerState,
        Timestamps timestamps,
        Links links
) {

    static PostResponse from(PostCard card) {
        String self = "/api/v1/posts/" + card.id();
        return new PostResponse(
                card.id(),
                card.title(),
                card.description(),
                new Author(
                        card.author().accountId(),
                        card.author().nickname(),
                        card.author().displayName(),
                        card.author().avatarUrl(),
                        card.author().avatarMediaId() == null
                                ? null
                                : MediaReferenceResponse.from(card.author().avatarMediaId())
                ),
                card.postType(),
                card.labels(),
                card.media().stream()
                        .map(item -> new Media(item.type(), item.url()))
                        .toList(),
                new Counters(
                        card.counters().likes(),
                        card.counters().comments(),
                        card.counters().reposts()
                ),
                new ViewerState(
                        card.viewerState().liked(),
                        card.viewerState().bookmarked(),
                        card.viewerState().reposted(),
                        card.viewerState().isAuthor(),
                        card.viewerState().canEdit(),
                        card.viewerState().canDelete()
                ),
                new Timestamps(
                        card.timestamps().createdAt(),
                        card.timestamps().updatedAt()
                ),
                new Links(self, self + "#comments")
        );
    }

    public record Author(
            UUID accountId,
            String nickname,
            String displayName,
            @Schema(types = {"string", "null"})
            String avatarUrl,
            MediaReferenceResponse avatar
    ) {
    }

    public record Media(
            PostMediaType type,
            @Schema(minLength = 1, maxLength = 2_048)
            URI url
    ) {
    }

    public record Counters(
            long likes,
            long comments,
            long reposts
    ) {
    }

    public record ViewerState(
            boolean liked,
            boolean bookmarked,
            boolean reposted,
            @JsonProperty("isAuthor") boolean isAuthor,
            boolean canEdit,
            boolean canDelete
    ) {
    }

    public record Timestamps(
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record Links(
            String self,
            String comments
    ) {
    }
}
