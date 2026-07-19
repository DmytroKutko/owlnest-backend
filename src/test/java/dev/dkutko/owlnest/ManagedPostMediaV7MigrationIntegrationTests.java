package dev.dkutko.owlnest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ManagedPostMediaV7MigrationIntegrationTests {

    private static final Instant BASE_TIME = Instant.parse("2026-07-19T10:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Test
    void upgradesPopulatedV6PreservesLegacyRowsAndEnforcesManagedPostImageShape() {
        String schema = "post_media_v7_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            migrate(schema, "6");
            UUID accountId = insertAccount(jdbc, schema);
            UUID postId = insertPost(jdbc, schema, accountId);
            List<LegacyMediaRow> legacyRows = List.of(
                    new LegacyMediaRow(postId, 0, "IMAGE", "https://cdn.example.com/%E2%9C%93.png?x=1#raw"),
                    new LegacyMediaRow(postId, 1, "VIDEO", "https://user:pass@cdn.example.com:443/v.mp4")
            );
            legacyRows.forEach(row -> jdbc.update(
                    "INSERT INTO " + schema
                            + ".post_media (post_id, position, media_type, url) VALUES (?, ?, ?, ?)",
                    row.postId(), row.position(), row.mediaType(), row.url()
            ));
            UUID postImageId = insertReadyMedia(jdbc, schema, accountId, "POST_IMAGE");
            UUID avatarId = insertReadyMedia(jdbc, schema, accountId, "AVATAR");

            migrate(schema, "7");

            assertThat(jdbc.query(
                    "SELECT post_id, position, media_type, url FROM " + schema
                            + ".post_media WHERE post_id = ? ORDER BY position",
                    (resultSet, rowNumber) -> new LegacyMediaRow(
                            resultSet.getObject("post_id", UUID.class),
                            resultSet.getInt("position"),
                            resultSet.getString("media_type"),
                            resultSet.getString("url")
                    ),
                    postId
            )).containsExactlyElementsOf(legacyRows);

            assertCatalog(jdbc, schema);
            jdbc.update(
                    "INSERT INTO " + schema
                            + ".post_media (post_id, position, media_type, url) VALUES (?, 2, 'IMAGE', ?)",
                    postId,
                    "https://cdn.example.com/after-v7.webp?exact=%2F"
            );
            jdbc.update(
                    "INSERT INTO " + schema
                            + ".post_media (post_id, position, media_type, managed_media_id)"
                            + " VALUES (?, 3, 'IMAGE', ?)",
                    postId,
                    postImageId
            );
            assertThat(jdbc.queryForMap(
                    "SELECT url, managed_media_id, managed_media_purpose FROM " + schema
                            + ".post_media WHERE post_id = ? AND position = 3",
                    postId
            )).containsAllEntriesOf(Map.of(
                    "managed_media_id", postImageId,
                    "managed_media_purpose", "POST_IMAGE"
            ));

            assertRejected(jdbc, schema, postId, 4, "'IMAGE', NULL, NULL", "ck_post_media_exactly_one_source");
            assertRejected(jdbc, schema, postId, 5, "'IMAGE', 'https://cdn.example.com/both.png', '"
                    + postImageId + "'", "ck_post_media_exactly_one_source");
            assertRejected(jdbc, schema, postId, 6, "'VIDEO', NULL, '"
                    + postImageId + "'", "ck_post_media_managed_type");
            assertRejected(jdbc, schema, postId, 7, "'IMAGE', NULL, '"
                    + avatarId + "'", "fk_post_media_managed_media");
            assertRejected(jdbc, schema, postId, 8, "'IMAGE', NULL, '"
                    + postImageId + "'", "uq_post_media_managed_media_id");
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static void assertCatalog(JdbcTemplate jdbc, String schema) {
        Map<String, String> columns = jdbc.query(
                """
                        SELECT column_name, data_type, is_nullable, is_generated, generation_expression
                        FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'post_media'
                          AND column_name IN ('url', 'managed_media_id', 'managed_media_purpose')
                        """,
                resultSet -> {
                    Map<String, String> values = new java.util.LinkedHashMap<>();
                    while (resultSet.next()) {
                        values.put(
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type") + "|"
                                        + resultSet.getString("is_nullable") + "|"
                                        + resultSet.getString("is_generated") + "|"
                                        + resultSet.getString("generation_expression")
                        );
                    }
                    return values;
                },
                schema
        );
        assertThat(columns.get("url")).startsWith("character varying|YES|NEVER|");
        assertThat(columns.get("managed_media_id")).startsWith("uuid|YES|NEVER|");
        assertThat(columns.get("managed_media_purpose"))
                .startsWith("character varying|YES|ALWAYS|")
                .contains("POST_IMAGE");

        Set<String> constraints = Set.copyOf(jdbc.queryForList(
                """
                        SELECT conname
                        FROM pg_constraint
                        WHERE connamespace = ?::regnamespace
                          AND conname IN (
                              'uq_post_media_managed_media_id',
                              'ck_post_media_exactly_one_source',
                              'ck_post_media_managed_type',
                              'fk_post_media_managed_media'
                          )
                        """,
                String.class,
                schema
        ));
        assertThat(constraints).containsExactlyInAnyOrder(
                "uq_post_media_managed_media_id",
                "ck_post_media_exactly_one_source",
                "ck_post_media_managed_type",
                "fk_post_media_managed_media"
        );
    }

    private static void assertRejected(
            JdbcTemplate jdbc,
            String schema,
            UUID postId,
            int position,
            String sourceValues,
            String constraint
    ) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO " + schema
                        + ".post_media (post_id, position, media_type, url, managed_media_id) VALUES ('"
                        + postId + "', " + position + ", " + sourceValues + ")"
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(target))
                .load()
                .migrate();
    }

    private static UUID insertAccount(JdbcTemplate jdbc, String schema) {
        UUID accountId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO %s.identity_account (
                            id, provider, external_subject, email,
                            email_verified, created_at, last_seen_at
                        ) VALUES (?, 'keycloak', ?, ?, TRUE, ?, ?)
                        """.formatted(schema),
                accountId,
                "v7-subject-" + accountId,
                accountId + "@example.com",
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME)
        );
        return accountId;
    }

    private static UUID insertPost(JdbcTemplate jdbc, String schema, UUID accountId) {
        UUID postId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO %s.post (
                            id, author_id, title, description, post_type,
                            like_count, comment_count, repost_count, created_at, updated_at
                        ) VALUES (?, ?, NULL, 'V7 migration', 'PERSONAL', 0, 0, 0, ?, ?)
                        """.formatted(schema),
                postId,
                accountId,
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME)
        );
        return postId;
    }

    private static UUID insertReadyMedia(
            JdbcTemplate jdbc,
            String schema,
            UUID accountId,
            String purpose
    ) {
        UUID mediaId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO %s.managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            observed_content_type, observed_size_bytes, object_etag,
                            upload_expires_at, ready_at, ready_expires_at,
                            cleanup_attempt_count, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, 'image/webp', 42, 'READY',
                                  'image/webp', 42, ?, ?, ?, ?, 0, ?, ?)
                        """.formatted(schema),
                mediaId,
                accountId,
                purpose,
                "managed/tests/" + mediaId,
                "etag-" + mediaId,
                Timestamp.from(BASE_TIME.plusSeconds(900)),
                Timestamp.from(BASE_TIME.plusSeconds(1)),
                Timestamp.from(BASE_TIME.plusSeconds(86_400)),
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME.plusSeconds(1))
        );
        return mediaId;
    }

    private record LegacyMediaRow(UUID postId, int position, String mediaType, String url) {
    }
}
