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
            INSERT INTO post_media (post_id, position, media_type, url)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostContentRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                mediaArguments.add(new Object[]{postId, position, item.type().name(), item.url().toString()});
            }
            jdbcTemplate.batchUpdate(INSERT_MEDIA_SQL, mediaArguments);
        }
    }
}
