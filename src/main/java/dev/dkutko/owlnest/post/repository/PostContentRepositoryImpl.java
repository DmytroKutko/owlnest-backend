package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostMedia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class PostContentRepositoryImpl implements PostContentRepository {

    private static final String INSERT_LABEL_SQL = """
            INSERT INTO post_label (post_id, position, label)
            VALUES (?, ?, ?)
            """;

    private static final String INSERT_MEDIA_SQL = """
            INSERT INTO post_media (post_id, position, media_type, url, managed_media_id)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostContentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UUID> findManagedMediaIds(UUID postId) {
        return jdbcTemplate.queryForList(
                "SELECT managed_media_id FROM post_media WHERE post_id = ? AND managed_media_id IS NOT NULL ORDER BY managed_media_id",
                UUID.class,
                postId
        );
    }

    @Override
    public boolean hasActivePostImageAssociation(UUID mediaId, UUID ownerAccountId) {
        Boolean associated = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM post_media media
                            JOIN post p ON p.id = media.post_id
                            WHERE media.managed_media_id = ?
                              AND p.author_id = ?
                              AND p.deleted_at IS NULL
                        )
                        """,
                Boolean.class,
                mediaId,
                ownerAccountId
        );
        return Boolean.TRUE.equals(associated);
    }

    @Override
    public void replace(UUID postId, List<String> labels, List<PostMedia> media) {
        jdbcTemplate.update("DELETE FROM post_label WHERE post_id = ?", postId);
        jdbcTemplate.update("DELETE FROM post_media WHERE post_id = ?", postId);

        if (!labels.isEmpty()) {
            List<Object[]> labelArguments = new ArrayList<>(labels.size());
            for (int position = 0; position < labels.size(); position++) {
                labelArguments.add(new Object[]{postId, position, labels.get(position)});
            }
            jdbcTemplate.batchUpdate(INSERT_LABEL_SQL, labelArguments);
        }

        if (!media.isEmpty()) {
            List<Object[]> mediaArguments = new ArrayList<>(media.size());
            for (int position = 0; position < media.size(); position++) {
                PostMedia item = media.get(position);
                mediaArguments.add(new Object[]{
                        postId,
                        position,
                        item.type().name(),
                        item.url() == null ? null : item.url().toString(),
                        item.managedMediaId()
                });
            }
            jdbcTemplate.batchUpdate(INSERT_MEDIA_SQL, mediaArguments);
        }
    }
}
