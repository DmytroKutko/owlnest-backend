package dev.dkutko.owlnest.post.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
public class PostInteractionRepositoryImpl implements PostInteractionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PostInteractionRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean addLike(UUID postId, UUID accountId, Instant createdAt) {
        return insertRelation("post_like", postId, accountId, createdAt);
    }

    @Override
    public boolean removeLike(UUID postId, UUID accountId) {
        return deleteRelation("post_like", postId, accountId);
    }

    @Override
    public boolean addBookmark(UUID postId, UUID accountId, Instant createdAt) {
        return insertRelation("post_bookmark", postId, accountId, createdAt);
    }

    @Override
    public boolean removeBookmark(UUID postId, UUID accountId) {
        return deleteRelation("post_bookmark", postId, accountId);
    }

    @Override
    public boolean addRepost(UUID postId, UUID accountId, Instant createdAt) {
        return insertRelation("post_repost", postId, accountId, createdAt);
    }

    @Override
    public boolean removeRepost(UUID postId, UUID accountId) {
        return deleteRelation("post_repost", postId, accountId);
    }

    @Override
    public boolean adjustLikeCount(UUID postId, int delta) {
        return adjustCounter("like_count", postId, delta);
    }

    @Override
    public boolean adjustRepostCount(UUID postId, int delta) {
        return adjustCounter("repost_count", postId, delta);
    }

    private boolean insertRelation(String table, UUID postId, UUID accountId, Instant createdAt) {
        String sql = "INSERT INTO " + table + " (post_id, account_id, created_at) "
                + "VALUES (?, ?, ?) ON CONFLICT (post_id, account_id) DO NOTHING";
        return jdbcTemplate.update(sql, postId, accountId, Timestamp.from(createdAt)) == 1;
    }

    private boolean deleteRelation(String table, UUID postId, UUID accountId) {
        String sql = "DELETE FROM " + table + " WHERE post_id = ? AND account_id = ?";
        return jdbcTemplate.update(sql, postId, accountId) == 1;
    }

    private boolean adjustCounter(String column, UUID postId, int delta) {
        String sql = "UPDATE post SET " + column + " = " + column + " + ? "
                + "WHERE id = ? AND deleted_at IS NULL AND " + column + " + ? >= 0";
        return jdbcTemplate.update(sql, delta, postId, delta) == 1;
    }
}
