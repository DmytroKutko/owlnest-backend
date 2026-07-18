package dev.dkutko.owlnest.post.service;

import java.util.List;

public record PostCommentPage(
        List<PostCommentItem> items,
        int limit,
        boolean hasMore,
        String nextCursor
) {

    public PostCommentPage {
        items = List.copyOf(items);
    }
}
