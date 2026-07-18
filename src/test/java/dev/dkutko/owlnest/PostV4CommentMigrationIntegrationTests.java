package dev.dkutko.owlnest;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PostV4CommentMigrationIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void appliesV4AndV5WithExpectedCatalogConstraintsForeignKeysAndIndexes() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '4'",
                Boolean.class
        )).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '5'",
                Boolean.class
        )).isTrue();

        Map<String, String> columns = jdbcTemplate.query(
                """
                        SELECT column_name, data_type
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'post_comment'
                        ORDER BY ordinal_position
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("column_name"), resultSet.getString("data_type"));
                    }
                    return result;
                }
        );
        assertThat(columns).containsExactly(
                Map.entry("id", "uuid"),
                Map.entry("post_id", "uuid"),
                Map.entry("author_id", "uuid"),
                Map.entry("text_content", "text"),
                Map.entry("created_at", "timestamp with time zone")
        );

        Set<String> constraints = Set.copyOf(jdbcTemplate.queryForList(
                """
                        SELECT conname
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname IN (
                              'pk_post_comment',
                              'fk_post_comment_post',
                              'fk_post_comment_author',
                              'ck_post_comment_text',
                              'ck_post_comment_count'
                          )
                        """,
                String.class
        ));
        assertThat(constraints).containsExactlyInAnyOrder(
                "pk_post_comment",
                "fk_post_comment_post",
                "fk_post_comment_author",
                "ck_post_comment_text",
                "ck_post_comment_count"
        );

        Map<String, String> foreignKeyDeleteActions = jdbcTemplate.query(
                """
                        SELECT conname, confdeltype
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname IN ('fk_post_comment_post', 'fk_post_comment_author')
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("conname"), resultSet.getString("confdeltype"));
                    }
                    return result;
                }
        );
        assertThat(foreignKeyDeleteActions).containsExactlyInAnyOrderEntriesOf(Map.of(
                "fk_post_comment_post", "c",
                "fk_post_comment_author", "r"
        ));

        Map<String, String> indexDefinitions = jdbcTemplate.queryForList(
                        """
                                SELECT indexname, indexdef
                                FROM pg_indexes
                                WHERE schemaname = current_schema()
                                  AND tablename = 'post_comment'
                                """
                ).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("indexname"),
                        row -> (String) row.get("indexdef")
                ));
        assertThat(indexDefinitions).containsKeys(
                "pk_post_comment",
                "idx_post_comment_post_created_id",
                "idx_post_comment_author"
        );
        assertThat(indexDefinitions.get("idx_post_comment_post_created_id"))
                .contains("post_id", "created_at", "id");

        String countConstraint = jdbcTemplate.queryForObject(
                """
                        SELECT pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname = 'ck_post_comment_count'
                        """,
                String.class
        );
        assertThat(countConstraint)
                .containsIgnoringCase("CHECK")
                .contains("comment_count >= 0")
                .doesNotContain("comment_count = 0");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT convalidated
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname = 'ck_post_comment_count'
                        """,
                Boolean.class
        )).isTrue();
    }

    @Test
    void upgradesPopulatedV3SchemaThroughV4AndV5WithValidatedNonnegativeCounter() {
        String schema = "comment_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("3"))
                    .load()
                    .migrate();

            UUID firstAuthorId = insertUpgradeAccount(upgradeJdbc, schema);
            UUID secondAuthorId = insertUpgradeAccount(upgradeJdbc, schema);
            UUID firstPostId = insertUpgradePost(upgradeJdbc, schema, firstAuthorId, "First legacy V3 post");
            UUID secondPostId = insertUpgradePost(upgradeJdbc, schema, secondAuthorId, "Second legacy V3 post");
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".post WHERE comment_count = 0",
                    Integer.class
            )).isEqualTo(2);

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(upgradeJdbc.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = ?
                              AND table_name = 'post_comment'
                            """,
                    Integer.class,
                    schema
            )).isEqualTo(1);
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT success FROM " + schema + ".flyway_schema_history WHERE version = '4'",
                    Boolean.class
            )).isTrue();
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT success FROM " + schema + ".flyway_schema_history WHERE version = '5'",
                    Boolean.class
            )).isTrue();

            upgradeJdbc.update(
                    "UPDATE " + schema + ".post SET comment_count = 3 WHERE id = ?",
                    firstPostId
            );
            upgradeJdbc.update(
                    "UPDATE " + schema + ".post SET comment_count = 1 WHERE id = ?",
                    secondPostId
            );
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT SUM(comment_count) FROM " + schema + ".post",
                    Long.class
            )).isEqualTo(4L);
            assertThatThrownBy(() -> upgradeJdbc.update(
                    "UPDATE " + schema + ".post SET comment_count = -1 WHERE id = ?",
                    firstPostId
            ))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_post_comment_count");

            assertThat(upgradeJdbc.queryForObject(
                    """
                            SELECT constraint_definition.convalidated
                            FROM pg_constraint constraint_definition
                            JOIN pg_namespace namespace
                              ON namespace.oid = constraint_definition.connamespace
                            WHERE namespace.nspname = ?
                              AND constraint_definition.conname = 'ck_post_comment_count'
                            """,
                    Boolean.class,
                    schema
            )).isTrue();
        } finally {
            upgradeJdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void enforcesCommentTextForeignKeysAndFinalNonnegativeCounterConstraint() {
        UUID postAuthorId = insertAccount();
        UUID commentAuthorId = insertAccount();
        UUID postId = insertPost(postAuthorId);
        try {
            String exactLimitText = "😀".repeat(5_000);
            UUID validCommentId = insertComment(postId, commentAuthorId, exactLimitText);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT CHAR_LENGTH(text_content) FROM post_comment WHERE id = ?",
                    Integer.class,
                    validCommentId
            )).isEqualTo(5_000);

            for (String invalidText : new String[]{
                    " \t\n\r ",
                    "\u0001\u001f",
                    "😀".repeat(5_001)
            }) {
                assertConstraintViolation(
                        "ck_post_comment_text",
                        () -> insertComment(postId, commentAuthorId, invalidText)
                );
            }
            assertConstraintViolation(
                    "fk_post_comment_post",
                    () -> insertComment(UUID.randomUUID(), commentAuthorId, "Missing post")
            );
            assertConstraintViolation(
                    "fk_post_comment_author",
                    () -> insertComment(postId, UUID.randomUUID(), "Missing author")
            );

            jdbcTemplate.update("UPDATE post SET comment_count = 1 WHERE id = ?", postId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT comment_count FROM post WHERE id = ?",
                    Long.class,
                    postId
            )).isEqualTo(1L);
            assertConstraintViolation(
                    "ck_post_comment_count",
                    () -> jdbcTemplate.update("UPDATE post SET comment_count = -1 WHERE id = ?", postId)
            );

            assertConstraintViolation(
                    "fk_post_comment_author",
                    () -> jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", commentAuthorId)
            );
            assertThat(jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_comment WHERE post_id = ?",
                    Integer.class,
                    postId
            )).isZero();
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update(
                    "DELETE FROM identity_account WHERE id IN (?, ?)",
                    postAuthorId,
                    commentAuthorId
            );
        }
    }

    private UUID insertAccount() {
        UUID accountId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO identity_account (
                            id, provider, external_subject, email, email_verified, created_at, last_seen_at
                        ) VALUES (?, 'test', ?, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                accountId,
                "comment-migration-test-" + accountId
        );
        return accountId;
    }

    private UUID insertUpgradeAccount(JdbcTemplate upgradeJdbc, String schema) {
        UUID accountId = UUID.randomUUID();
        upgradeJdbc.update(
                """
                        INSERT INTO %s.identity_account (
                            id, provider, external_subject, email, email_verified, created_at, last_seen_at
                        ) VALUES (?, 'test', ?, NULL, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.formatted(schema),
                accountId,
                "comment-upgrade-test-" + accountId
        );
        return accountId;
    }

    private UUID insertUpgradePost(
            JdbcTemplate upgradeJdbc,
            String schema,
            UUID authorId,
            String description
    ) {
        UUID postId = UUID.randomUUID();
        upgradeJdbc.update(
                """
                        INSERT INTO %s.post (
                            id, author_id, title, description, post_type, created_at, updated_at
                        ) VALUES (?, ?, NULL, ?, 'PERSONAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """.formatted(schema),
                postId,
                authorId,
                description
        );
        return postId;
    }

    private UUID insertPost(UUID authorId) {
        UUID postId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO post (
                            id, author_id, title, description, post_type, created_at, updated_at
                        ) VALUES (?, ?, NULL, 'Comment migration target', 'PERSONAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                postId,
                authorId
        );
        return postId;
    }

    private UUID insertComment(UUID postId, UUID authorId, String text) {
        UUID commentId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO post_comment (id, post_id, author_id, text_content, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                commentId,
                postId,
                authorId,
                text,
                Timestamp.from(Instant.now())
        );
        return commentId;
    }

    private void assertConstraintViolation(String constraintName, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintName);
    }
}
