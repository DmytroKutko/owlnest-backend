package dev.dkutko.owlnest.post.repository;

import java.time.Instant;
import java.util.UUID;

public interface PostInteractionRepository {

    boolean addLike(UUID postId, UUID accountId, Instant createdAt);

    boolean removeLike(UUID postId, UUID accountId);

    boolean addBookmark(UUID postId, UUID accountId, Instant createdAt);

    boolean removeBookmark(UUID postId, UUID accountId);

    boolean addRepost(UUID postId, UUID accountId, Instant createdAt);

    boolean removeRepost(UUID postId, UUID accountId);

    boolean adjustLikeCount(UUID postId, int delta);

    boolean adjustRepostCount(UUID postId, int delta);
}
