package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostComment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostCommentRepository {

    Instant nextCreatedAt(UUID postId);

    void save(PostComment comment);

    void flush();

    Optional<CommentPageRows> findActivePage(
            UUID postId,
            Instant afterCreatedAt,
            UUID afterId,
            int fetchLimit
    );

    record CommentPageRows(List<CommentRow> comments) {

        public CommentPageRows {
            comments = List.copyOf(comments);
        }
    }

    record CommentRow(
            UUID id,
            UUID postId,
            UUID authorId,
            String text,
            Instant createdAt
    ) {
    }
}
