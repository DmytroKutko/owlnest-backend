package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.repository.PostCardQueryRepository;
import dev.dkutko.owlnest.post.repository.PostCardQueryRepository.PostCardRow;
import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ListPostsService {

    private static final int MAX_LIMIT = 100;

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostCardQueryRepository postCardQueryRepository;
    private final PostCardQueryService postCardQueryService;

    public ListPostsService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostCardQueryRepository postCardQueryRepository,
            PostCardQueryService postCardQueryService
    ) {
        this.currentProfileService = currentProfileService;
        this.postCardQueryRepository = postCardQueryRepository;
        this.postCardQueryService = postCardQueryService;
    }

    @Transactional
    public PostListPage list(int limit, String cursor) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Post page limit must be between 1 and 100");
        }

        CurrentProfile viewer = currentProfileService.getOrCreate();
        PostListCursorCodec.Position position = cursor == null
                ? null
                : PostListCursorCodec.decode(cursor);
        Instant beforeCreatedAt = position == null ? null : position.createdAt();
        UUID beforePostId = position == null ? null : position.postId();
        List<PostCardRow> fetchedRows = postCardQueryRepository.findGlobalActivePage(
                viewer.accountId(),
                beforeCreatedAt,
                beforePostId,
                limit + 1
        );

        boolean hasMore = fetchedRows.size() > limit;
        List<PostCardRow> visibleRows = new ArrayList<>(
                fetchedRows.subList(0, Math.min(limit, fetchedRows.size()))
        );
        List<PostCard> items = postCardQueryService.toCards(visibleRows, viewer.accountId());
        String nextCursor = hasMore
                ? encodeNextCursor(visibleRows.getLast())
                : null;
        return new PostListPage(items, limit, hasMore, nextCursor);
    }

    private static String encodeNextCursor(PostCardRow row) {
        return PostListCursorCodec.encode(row.createdAt(), row.id());
    }
}
