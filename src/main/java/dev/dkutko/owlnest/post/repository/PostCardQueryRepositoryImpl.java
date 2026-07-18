package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostMedia;
import dev.dkutko.owlnest.post.domain.PostMediaType;
import dev.dkutko.owlnest.post.domain.PostType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostCardQueryRepositoryImpl implements PostCardQueryRepository {

    private static final String FIND_ACTIVE_CARD_SQL = """
            SELECT p.id,
                   p.author_id,
                   p.post_type,
                   p.title,
                   p.description,
                   p.like_count,
                   p.comment_count,
                   p.repost_count,
                   p.created_at,
                   p.updated_at,
                   ARRAY(
                       SELECT label.label
                       FROM post_label label
                       WHERE label.post_id = p.id
                       ORDER BY label.position
                   ) AS labels,
                   ARRAY(
                       SELECT media.media_type
                       FROM post_media media
                       WHERE media.post_id = p.id
                       ORDER BY media.position
                   ) AS media_types,
                   ARRAY(
                       SELECT media.url
                       FROM post_media media
                       WHERE media.post_id = p.id
                       ORDER BY media.position
                   ) AS media_urls,
                   EXISTS (
                       SELECT 1 FROM post_like interaction
                       WHERE interaction.post_id = p.id AND interaction.account_id = ?
                   ) AS liked,
                   EXISTS (
                       SELECT 1 FROM post_bookmark interaction
                       WHERE interaction.post_id = p.id AND interaction.account_id = ?
                   ) AS bookmarked,
                   EXISTS (
                       SELECT 1 FROM post_repost interaction
                       WHERE interaction.post_id = p.id AND interaction.account_id = ?
                   ) AS reposted
            FROM post p
            WHERE p.id = ? AND p.deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostCardQueryRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PostCardRow> findActiveById(UUID postId, UUID viewerAccountId) {
        List<PostCardRow> rows = jdbcTemplate.query(
                FIND_ACTIVE_CARD_SQL,
                (resultSet, rowNumber) -> new PostCardRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("author_id", UUID.class),
                        PostType.valueOf(resultSet.getString("post_type")),
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        readTextArray(resultSet, "labels"),
                        readMedia(resultSet),
                        resultSet.getLong("like_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getLong("repost_count"),
                        resultSet.getBoolean("liked"),
                        resultSet.getBoolean("bookmarked"),
                        resultSet.getBoolean("reposted"),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant()
                ),
                viewerAccountId,
                viewerAccountId,
                viewerAccountId,
                postId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    private static List<String> readTextArray(ResultSet resultSet, String column) throws SQLException {
        Array sqlArray = resultSet.getArray(column);
        Object[] values = (Object[]) sqlArray.getArray();
        List<String> strings = new ArrayList<>(values.length);
        for (Object value : values) {
            strings.add((String) value);
        }
        return List.copyOf(strings);
    }

    private static List<PostMedia> readMedia(ResultSet resultSet) throws SQLException {
        List<String> types = readTextArray(resultSet, "media_types");
        List<String> urls = readTextArray(resultSet, "media_urls");
        List<PostMedia> media = new ArrayList<>(types.size());
        for (int index = 0; index < types.size(); index++) {
            media.add(new PostMedia(
                    PostMediaType.valueOf(types.get(index)),
                    URI.create(urls.get(index))
            ));
        }
        return List.copyOf(media);
    }
}
