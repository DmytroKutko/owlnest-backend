package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.profile.service.ProfileSummary;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PostCommentItem(
        UUID id,
        UUID postId,
        String text,
        ProfileSummary author,
        Instant createdAt
) {

    public PostCommentItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
