package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.domain.PostMedia;
import dev.dkutko.owlnest.post.domain.PostType;
import dev.dkutko.owlnest.profile.service.ProfileSummary;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostCard(
        UUID id,
        PostType postType,
        String title,
        String description,
        List<String> labels,
        List<PostMedia> media,
        ProfileSummary author,
        Counters counters,
        ViewerState viewerState,
        Timestamps timestamps
) {

    public PostCard {
        labels = List.copyOf(labels);
        media = List.copyOf(media);
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
            boolean isAuthor,
            boolean canEdit,
            boolean canDelete
    ) {
    }

    public record Timestamps(
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
