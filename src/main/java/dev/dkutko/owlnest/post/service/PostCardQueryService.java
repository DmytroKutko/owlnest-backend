package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.repository.PostCardQueryRepository;
import dev.dkutko.owlnest.post.repository.PostCardQueryRepository.PostCardRow;
import dev.dkutko.owlnest.profile.service.GetProfileSummaryService;
import dev.dkutko.owlnest.profile.service.ProfileSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PostCardQueryService {

    private final PostCardQueryRepository postCardQueryRepository;
    private final GetProfileSummaryService getProfileSummaryService;

    public PostCardQueryService(
            PostCardQueryRepository postCardQueryRepository,
            GetProfileSummaryService getProfileSummaryService
    ) {
        this.postCardQueryRepository = postCardQueryRepository;
        this.getProfileSummaryService = getProfileSummaryService;
    }

    @Transactional(readOnly = true)
    public PostCard getById(UUID postId, UUID viewerAccountId) {
        PostCardRow row = postCardQueryRepository.findActiveById(postId, viewerAccountId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        return toCards(List.of(row), viewerAccountId).getFirst();
    }

    List<PostCard> toCards(List<PostCardRow> rows, UUID viewerAccountId) {
        Set<UUID> authorIds = new LinkedHashSet<>();
        for (PostCardRow row : rows) {
            authorIds.add(row.authorId());
        }
        Map<UUID, ProfileSummary> authors = getProfileSummaryService.getByAccountIds(authorIds);
        return rows.stream()
                .map(row -> toCard(row, authors.get(row.authorId()), viewerAccountId))
                .toList();
    }

    private static PostCard toCard(PostCardRow row, ProfileSummary author, UUID viewerAccountId) {
        boolean isAuthor = row.authorId().equals(viewerAccountId);
        return new PostCard(
                row.id(),
                row.postType(),
                row.title(),
                row.description(),
                row.labels(),
                row.media(),
                author,
                new PostCard.Counters(row.likeCount(), row.commentCount(), row.repostCount()),
                new PostCard.ViewerState(
                        row.liked(),
                        row.bookmarked(),
                        row.reposted(),
                        isAuthor,
                        isAuthor,
                        isAuthor
                ),
                new PostCard.Timestamps(row.createdAt(), row.updatedAt())
        );
    }
}
