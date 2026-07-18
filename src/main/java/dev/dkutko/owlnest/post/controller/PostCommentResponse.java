package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.PostCommentItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record PostCommentResponse(
        UUID id,
        UUID postId,
        @Schema(minLength = 1, maxLength = 5_000)
        String text,
        CommentAuthor author,
        Instant createdAt,
        CommentLinks links
) {

    static PostCommentResponse from(PostCommentItem item) {
        String post = "/api/v1/posts/" + item.postId();
        String collection = post + "/comments";
        String self = collection + "/" + item.id();
        return new PostCommentResponse(
                item.id(),
                item.postId(),
                item.text(),
                new CommentAuthor(
                        item.author().accountId(),
                        item.author().nickname(),
                        item.author().displayName(),
                        item.author().avatarUrl()
                ),
                item.createdAt(),
                new CommentLinks(self, post, collection)
        );
    }

    public record CommentAuthor(
            UUID accountId,
            String nickname,
            String displayName,
            @Schema(types = {"string", "null"})
            String avatarUrl
    ) {
    }

    public record CommentLinks(
            String self,
            String post,
            String collection
    ) {
    }
}
