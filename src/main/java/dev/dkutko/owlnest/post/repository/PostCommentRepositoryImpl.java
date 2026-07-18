package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostComment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostCommentRepositoryImpl implements PostCommentRepository {

    private static final String NEXT_CREATED_AT_SQL = """
            SELECT GREATEST(
                       clock_timestamp(),
                       COALESCE(
                           (
                               SELECT comment.created_at + INTERVAL '1 microsecond'
                               FROM post_comment comment
                               WHERE comment.post_id = ?
                               ORDER BY comment.created_at DESC, comment.id DESC
                               LIMIT 1
                           ),
                           '-infinity'::TIMESTAMPTZ
                       )
                   ) AS next_created_at
            """;

    private static final String FIND_FIRST_PAGE_SQL = """
            SELECT active_post.id AS active_post_id,
                   page_comment.id AS comment_id,
                   page_comment.post_id,
                   page_comment.author_id,
                   page_comment.text_content,
                   page_comment.created_at
            FROM post active_post
            LEFT JOIN LATERAL (
                SELECT comment.id,
                       comment.post_id,
                       comment.author_id,
                       comment.text_content,
                       comment.created_at
                FROM post_comment comment
                WHERE comment.post_id = active_post.id
                ORDER BY comment.created_at, comment.id
                LIMIT ?
            ) page_comment ON TRUE
            WHERE active_post.id = ?
              AND active_post.deleted_at IS NULL
            ORDER BY page_comment.created_at ASC NULLS LAST,
                     page_comment.id ASC NULLS LAST
            """;

    private static final String FIND_AFTER_PAGE_SQL = """
            SELECT active_post.id AS active_post_id,
                   page_comment.id AS comment_id,
                   page_comment.post_id,
                   page_comment.author_id,
                   page_comment.text_content,
                   page_comment.created_at
            FROM post active_post
            LEFT JOIN LATERAL (
                SELECT comment.id,
                       comment.post_id,
                       comment.author_id,
                       comment.text_content,
                       comment.created_at
                FROM post_comment comment
                WHERE comment.post_id = active_post.id
                  AND (comment.created_at, comment.id) > (?, ?)
                ORDER BY comment.created_at, comment.id
                LIMIT ?
            ) page_comment ON TRUE
            WHERE active_post.id = ?
              AND active_post.deleted_at IS NULL
            ORDER BY page_comment.created_at ASC NULLS LAST,
                     page_comment.id ASC NULLS LAST
            """;

    private final SpringDataPostCommentRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public PostCommentRepositoryImpl(
            SpringDataPostCommentRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Instant nextCreatedAt(UUID postId) {
        return jdbcTemplate.queryForObject(
                NEXT_CREATED_AT_SQL,
                (resultSet, rowNumber) -> resultSet.getTimestamp("next_created_at").toInstant(),
                postId
        );
    }

    @Override
    public void save(PostComment comment) {
        repository.save(comment);
    }

    @Override
    public void flush() {
        repository.flush();
    }

    @Override
    public Optional<CommentPageRows> findActivePage(
            UUID postId,
            Instant afterCreatedAt,
            UUID afterId,
            int fetchLimit
    ) {
        if ((afterCreatedAt == null) != (afterId == null)) {
            throw new IllegalArgumentException("Comment page position must be complete");
        }
        if (fetchLimit < 1 || fetchLimit > 101) {
            throw new IllegalArgumentException("Comment page fetch limit must be between 1 and 101");
        }
        List<PageQueryRow> rows = afterCreatedAt == null
                ? jdbcTemplate.query(
                        FIND_FIRST_PAGE_SQL,
                        PostCommentRepositoryImpl::mapPageRow,
                        fetchLimit,
                        postId
                )
                : jdbcTemplate.query(
                        FIND_AFTER_PAGE_SQL,
                        PostCommentRepositoryImpl::mapPageRow,
                        Timestamp.from(afterCreatedAt),
                        afterId,
                        fetchLimit,
                        postId
                );
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        List<CommentRow> comments = new ArrayList<>(rows.size());
        for (PageQueryRow row : rows) {
            if (row.comment() != null) {
                comments.add(row.comment());
            }
        }
        return Optional.of(new CommentPageRows(comments));
    }

    private static PageQueryRow mapPageRow(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        UUID activePostId = resultSet.getObject("active_post_id", UUID.class);
        UUID commentId = resultSet.getObject("comment_id", UUID.class);
        if (commentId == null) {
            return new PageQueryRow(activePostId, null);
        }
        return new PageQueryRow(
                activePostId,
                new CommentRow(
                        commentId,
                        resultSet.getObject("post_id", UUID.class),
                        resultSet.getObject("author_id", UUID.class),
                        resultSet.getString("text_content"),
                        resultSet.getTimestamp("created_at").toInstant()
                )
        );
    }

    private record PageQueryRow(UUID activePostId, CommentRow comment) {
    }
}
