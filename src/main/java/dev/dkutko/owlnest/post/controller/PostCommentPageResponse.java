package dev.dkutko.owlnest.post.controller;

import dev.dkutko.owlnest.post.service.PostCommentPage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record PostCommentPageResponse(
        List<PostCommentResponse> items,
        PageMetadata page,
        PageLinks links
) {

    static PostCommentPageResponse from(UUID postId, String requestCursor, PostCommentPage commentPage) {
        String collection = "/api/v1/posts/" + postId + "/comments";
        String self = pageLink(collection, commentPage.limit(), requestCursor);
        String next = commentPage.hasMore()
                ? pageLink(collection, commentPage.limit(), commentPage.nextCursor())
                : null;
        return new PostCommentPageResponse(
                commentPage.items().stream()
                        .map(PostCommentResponse::from)
                        .toList(),
                new PageMetadata(
                        commentPage.limit(),
                        commentPage.hasMore(),
                        commentPage.nextCursor()
                ),
                new PageLinks(self, next, "/api/v1/posts/" + postId)
        );
    }

    private static String pageLink(String collection, int limit, String cursor) {
        String link = collection + "?limit=" + limit;
        return cursor == null ? link : link + "&cursor=" + cursor;
    }

    public record PageMetadata(
            int limit,
            boolean hasMore,
            @Schema(types = {"string", "null"}, maxLength = 1_024)
            String nextCursor
    ) {
    }

    public record PageLinks(
            String self,
            @Schema(types = {"string", "null"})
            String next,
            String post
    ) {
    }
}
