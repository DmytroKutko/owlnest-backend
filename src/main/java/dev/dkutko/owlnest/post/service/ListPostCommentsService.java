package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.repository.PostCommentRepository;
import dev.dkutko.owlnest.post.repository.PostCommentRepository.CommentPageRows;
import dev.dkutko.owlnest.post.repository.PostCommentRepository.CommentRow;
import dev.dkutko.owlnest.profile.service.GetProfileSummaryService;
import dev.dkutko.owlnest.profile.service.ProfileSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ListPostCommentsService {

    private static final int MAX_LIMIT = 100;

    private final PostCommentRepository postCommentRepository;
    private final GetProfileSummaryService profileSummaryService;

    public ListPostCommentsService(
            PostCommentRepository postCommentRepository,
            GetProfileSummaryService profileSummaryService
    ) {
        this.postCommentRepository = postCommentRepository;
        this.profileSummaryService = profileSummaryService;
    }

    @Transactional(readOnly = true)
    public PostCommentPage list(UUID postId, int limit, String cursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Comment page limit must be between 1 and 100");
        }

        PostCommentCursorCodec.Position position = cursor == null
                ? null
                : PostCommentCursorCodec.decode(cursor, postId);
        Instant afterCreatedAt = position == null ? null : position.createdAt();
        UUID afterId = position == null ? null : position.commentId();
        CommentPageRows pageRows = postCommentRepository.findActivePage(
                        postId,
                        afterCreatedAt,
                        afterId,
                        limit + 1
                )
                .orElseThrow(() -> new PostNotFoundException(postId));

        List<CommentRow> fetchedRows = pageRows.comments();
        boolean hasMore = fetchedRows.size() > limit;
        List<CommentRow> visibleRows = new ArrayList<>(
                fetchedRows.subList(0, Math.min(limit, fetchedRows.size()))
        );
        Set<UUID> authorIds = new LinkedHashSet<>();
        for (CommentRow row : visibleRows) {
            authorIds.add(row.authorId());
        }
        Map<UUID, ProfileSummary> authors = profileSummaryService.getByAccountIds(authorIds);

        List<PostCommentItem> items = visibleRows.stream()
                .map(row -> new PostCommentItem(
                        row.id(),
                        row.postId(),
                        row.text(),
                        authors.get(row.authorId()),
                        row.createdAt()
                ))
                .toList();
        String nextCursor = hasMore
                ? encodeNextCursor(postId, visibleRows.getLast())
                : null;
        return new PostCommentPage(items, limit, hasMore, nextCursor);
    }

    private static String encodeNextCursor(UUID postId, CommentRow row) {
        return PostCommentCursorCodec.encode(postId, row.createdAt(), row.id());
    }
}
