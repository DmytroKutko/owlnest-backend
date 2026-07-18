package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostMedia;
import dev.dkutko.owlnest.post.domain.PostType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostCardQueryRepository {

    Optional<PostCardRow> findActiveById(UUID postId, UUID viewerAccountId);

    List<PostCardRow> findGlobalActivePage(
            UUID viewerAccountId,
            Instant beforeCreatedAt,
            UUID beforePostId,
            int limit
    );

    record PostCardRow(
            UUID id,
            UUID authorId,
            PostType postType,
            String title,
            String description,
            List<String> labels,
            List<PostMedia> media,
            long likeCount,
            long commentCount,
            long repostCount,
            boolean liked,
            boolean bookmarked,
            boolean reposted,
            Instant createdAt,
            Instant updatedAt
    ) {

        public PostCardRow {
            labels = List.copyOf(labels);
            media = List.copyOf(media);
        }
    }
}
