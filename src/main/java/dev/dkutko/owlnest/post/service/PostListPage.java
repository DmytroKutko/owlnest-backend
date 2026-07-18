package dev.dkutko.owlnest.post.service;

import java.util.List;

public record PostListPage(
        List<PostCard> items,
        int limit,
        boolean hasMore,
        String nextCursor
) {

    public PostListPage {
        items = List.copyOf(items);
    }
}
