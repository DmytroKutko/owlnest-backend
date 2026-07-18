package dev.dkutko.owlnest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PostV3MigrationIntegrationTests {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "post",
            "post_label",
            "post_media",
            "post_like",
            "post_bookmark",
            "post_repost"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsExpectedTablesCompositePrimaryKeysConstraintsAndIndexes() {
        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = current_schema()
                          AND table_name IN (
                              'post', 'post_label', 'post_media',
                              'post_like', 'post_bookmark', 'post_repost'
                          )
                        """,
                String.class
        ));
        assertThat(tables).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        Map<String, String> primaryKeys = jdbcTemplate.query(
                """
                        SELECT table_class.relname AS table_name,
                               STRING_AGG(attribute.attname, ',' ORDER BY key_column.ordinality) AS columns
                        FROM pg_constraint constraint_definition
                        JOIN pg_class table_class
                          ON table_class.oid = constraint_definition.conrelid
                        JOIN pg_namespace namespace
                          ON namespace.oid = table_class.relnamespace
                        CROSS JOIN LATERAL UNNEST(constraint_definition.conkey)
                          WITH ORDINALITY AS key_column(attnum, ordinality)
                        JOIN pg_attribute attribute
                          ON attribute.attrelid = table_class.oid
                         AND attribute.attnum = key_column.attnum
                        WHERE namespace.nspname = current_schema()
                          AND constraint_definition.contype = 'p'
                          AND table_class.relname IN (
                              'post', 'post_label', 'post_media',
                              'post_like', 'post_bookmark', 'post_repost'
                          )
                        GROUP BY table_class.relname
                        """,
                resultSet -> {
                    Map<String, String> result = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("table_name"), resultSet.getString("columns"));
                    }
                    return result;
                }
        );
        assertThat(primaryKeys).containsAllEntriesOf(Map.of(
                "post", "id",
                "post_label", "post_id,position",
                "post_media", "post_id,position",
                "post_like", "post_id,account_id",
                "post_bookmark", "post_id,account_id",
                "post_repost", "post_id,account_id"
        ));

        Set<String> checkConstraints = Set.copyOf(jdbcTemplate.queryForList(
                """
                        SELECT constraint_definition.conname
                        FROM pg_constraint constraint_definition
                        JOIN pg_namespace namespace
                          ON namespace.oid = constraint_definition.connamespace
                        WHERE namespace.nspname = current_schema()
                          AND constraint_definition.contype = 'c'
                          AND constraint_definition.conname LIKE 'ck_post_%'
                        """,
                String.class
        ));
        assertThat(checkConstraints).contains(
                "ck_post_type",
                "ck_post_like_count",
                "ck_post_comment_count",
                "ck_post_repost_count",
                "ck_post_label_position",
                "ck_post_media_position",
                "ck_post_media_type",
                "ck_post_media_url"
        );

        Set<String> indexes = jdbcTemplate.queryForList(
                        """
                                SELECT indexname
                                FROM pg_indexes
                                WHERE schemaname = current_schema()
                                  AND tablename IN (
                                      'post', 'post_label', 'post_media',
                                      'post_like', 'post_bookmark', 'post_repost'
                                  )
                                """,
                        String.class
                ).stream()
                .collect(Collectors.toSet());
        assertThat(indexes).contains(
                "pk_post",
                "pk_post_label",
                "pk_post_media",
                "pk_post_like",
                "pk_post_bookmark",
                "pk_post_repost",
                "idx_post_author",
                "idx_post_like_account",
                "idx_post_bookmark_account",
                "idx_post_repost_account"
        );
        assertThat(indexes).doesNotContain("uq_post_label_post_lower_label");
    }

    @Test
    void enforcesCompositeKeysAndCheckConstraintsWhileAllowingDuplicateLabelValues() {
        UUID accountId = insertAccount();
        UUID postId = insertPost(accountId, "PERSONAL");
        try {
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, 'Spring')",
                    postId
            );
            assertConstraintViolation(
                    "pk_post_label",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, 'Other')",
                            postId
                    )
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 1, 'spring')",
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 2, 'Canonical')",
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 3, 'canonical')",
                    postId
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "\tCANONICAL"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "canonical\n"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "\u2003CANONICAL"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "canonical\u2003"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "\u2003"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 4, ?)",
                            postId,
                            "\1\37"
                    )
            );
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_label WHERE post_id = ? AND LOWER(label) = 'canonical'",
                    Integer.class,
                    postId
            )).isEqualTo(2);

            insertMedia(postId, 0, "https://cdn.example.com/assets/image.png?size=large&mode=fit#preview");
            insertMedia(postId, 1, "https://localhost:8443/valid.png");
            insertMedia(postId, 2, "https://192.0.2.10/valid.png");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM post_media WHERE post_id = ?",
                    Integer.class,
                    postId
            )).isEqualTo(3);
            assertConstraintViolation(
                    "pk_post_media",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_media (post_id, position, media_type, url) "
                                    + "VALUES (?, 0, 'VIDEO', 'https://cdn.example.com/duplicate.mp4')",
                            postId
                    )
            );

            assertConstraintViolation("ck_post_type", () -> insertPost(accountId, "GROUP"));
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update("UPDATE post SET description = ? WHERE id = ?", "\t\n", postId)
            );
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update("UPDATE post SET description = ? WHERE id = ?", "\u2003", postId)
            );
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update("UPDATE post SET description = ? WHERE id = ?", "\1\37", postId)
            );
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update(
                            "UPDATE post SET description = ? WHERE id = ?",
                            "d".repeat(20_001),
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update("UPDATE post SET title = ? WHERE id = ?", "\u2003Canonical", postId)
            );
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update("UPDATE post SET title = ? WHERE id = ?", "Canonical\37", postId)
            );
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update("UPDATE post SET title = ? WHERE id = ?", "\u2003", postId)
            );
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update("UPDATE post SET title = ? WHERE id = ?", "\1\37", postId)
            );
            assertConstraintViolation(
                    "ck_post_like_count",
                    () -> jdbcTemplate.update("UPDATE post SET like_count = -1 WHERE id = ?", postId)
            );
            assertConstraintViolation(
                    "ck_post_comment_count",
                    () -> jdbcTemplate.update("UPDATE post SET comment_count = 1 WHERE id = ?", postId)
            );
            assertConstraintViolation(
                    "ck_post_repost_count",
                    () -> jdbcTemplate.update("UPDATE post SET repost_count = -1 WHERE id = ?", postId)
            );
            assertConstraintViolation(
                    "ck_post_label_position",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 5, 'OutOfRange')",
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_media_position",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_media (post_id, position, media_type, url) "
                                    + "VALUES (?, 10, 'IMAGE', 'https://cdn.example.com/position.png')",
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_media_type",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_media (post_id, position, media_type, url) "
                                    + "VALUES (?, 4, 'AUDIO', 'https://cdn.example.com/audio.mp3')",
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_media_url",
                    () -> insertMedia(postId, 4, "http://cdn.example.com/insecure.png")
            );
            assertConstraintViolation(
                    "ck_post_media_url",
                    () -> insertMedia(postId, 4, "/relative.png")
            );
            for (String authoritylessHttpsUrl : new String[]{"https:x", "https:/x", "https:///x"}) {
                assertConstraintViolation(
                        "ck_post_media_url",
                        () -> insertMedia(postId, 4, authoritylessHttpsUrl)
                );
            }
            assertThatThrownBy(() -> insertMedia(
                    postId,
                    4,
                    "https://example.com/" + "a".repeat(2_029)
            )).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("additionalJavaSpaceCharacters")
    void rejectsAdditionalJavaSpaceCharactersAtStoredTextBoundaries(
            String scenario,
            String spaceCharacter
    ) {
        UUID accountId = insertAccount();
        UUID postId = insertPost(accountId, "PERSONAL");
        try {
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update(
                            "UPDATE post SET title = ? WHERE id = ?",
                            spaceCharacter + "Canonical",
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_title",
                    () -> jdbcTemplate.update(
                            "UPDATE post SET title = ? WHERE id = ?",
                            "Canonical" + spaceCharacter,
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update(
                            "UPDATE post SET description = ? WHERE id = ?",
                            spaceCharacter.repeat(2),
                            postId
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, ?)",
                            postId,
                            spaceCharacter + "Canonical"
                    )
            );
            assertConstraintViolation(
                    "ck_post_label_value",
                    () -> jdbcTemplate.update(
                            "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, ?)",
                            postId,
                            "Canonical" + spaceCharacter
                    )
            );
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @Test
    void enforcesUnicodeCodePointLengthsForAstralText() {
        String astralCharacter = "\uD83D\uDE00";
        UUID accountId = insertAccount();
        UUID postId = insertPost(accountId, "PERSONAL");
        try {
            jdbcTemplate.update(
                    "UPDATE post SET title = ?, description = ? WHERE id = ?",
                    astralCharacter.repeat(200),
                    astralCharacter.repeat(20_000),
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, ?)",
                    postId,
                    astralCharacter.repeat(50)
            );
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT CHAR_LENGTH(title) FROM post WHERE id = ?",
                    Integer.class,
                    postId
            )).isEqualTo(200);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT CHAR_LENGTH(description) FROM post WHERE id = ?",
                    Integer.class,
                    postId
            )).isEqualTo(20_000);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT CHAR_LENGTH(label) FROM post_label WHERE post_id = ? AND position = 0",
                    Integer.class,
                    postId
            )).isEqualTo(50);

            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE post SET title = ? WHERE id = ?",
                    astralCharacter.repeat(201),
                    postId
            )).isInstanceOf(DataIntegrityViolationException.class);
            assertConstraintViolation(
                    "ck_post_description",
                    () -> jdbcTemplate.update(
                            "UPDATE post SET description = ? WHERE id = ?",
                            astralCharacter.repeat(20_001),
                            postId
                    )
            );
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 1, ?)",
                    postId,
                    astralCharacter.repeat(51)
            )).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @Test
    void preservesDuplicateOrderedLabelsAtDatabaseBoundary() {
        UUID accountId = insertAccount();
        UUID postId = insertPost(accountId, "PERSONAL");
        try {
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, 'Spring')",
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 1, 'spring')",
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 2, 'Spring')",
                    postId
            );

            assertThat(jdbcTemplate.queryForList(
                    "SELECT label FROM post_label WHERE post_id = ? ORDER BY position",
                    String.class,
                    postId
            )).containsExactly("Spring", "spring", "Spring");
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @Test
    void acceptsUnicodeIpv6AndUserInfoMediaAtDatabaseBoundary() {
        UUID accountId = insertAccount();
        UUID postId = insertPost(accountId, "PERSONAL");
        try {
            String mediaUrlPrefix = "https://cdn.example.com/";
            String exactCodePointLimitUrl = mediaUrlPrefix + "😀".repeat(
                    2_048 - mediaUrlPrefix.codePointCount(0, mediaUrlPrefix.length())
            );
            List<String> urls = List.of(
                    "https://cdn.example.com/медіа/сова.png",
                    "https://[2001:db8::1]/image.png",
                    "https://reader@cdn.example.com/image.png",
                    "https://reader:secret@cdn.example.com/video.mp4",
                    exactCodePointLimitUrl
            );
            for (int position = 0; position < urls.size(); position++) {
                insertMedia(postId, position, urls.get(position));
            }

            assertThat(jdbcTemplate.queryForList(
                    "SELECT url FROM post_media WHERE post_id = ? ORDER BY position",
                    String.class,
                    postId
            )).containsExactlyElementsOf(urls);

            String aboveCodePointLimitUrl = mediaUrlPrefix + "😀".repeat(
                    2_049 - mediaUrlPrefix.codePointCount(0, mediaUrlPrefix.length())
            );
            assertThatThrownBy(() -> insertMedia(postId, urls.size(), aboveCodePointLimitUrl))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @Test
    void hardDeletingPostCascadesContentAndInteractionRows() {
        UUID authorId = insertAccount();
        UUID actorId = insertAccount();
        UUID postId = insertPost(authorId, "COMMUNITY");
        try {
            jdbcTemplate.update(
                    "INSERT INTO post_label (post_id, position, label) VALUES (?, 0, 'Cascade')",
                    postId
            );
            jdbcTemplate.update(
                    "INSERT INTO post_media (post_id, position, media_type, url) "
                            + "VALUES (?, 0, 'VIDEO', 'https://cdn.example.com/cascade.mp4')",
                    postId
            );
            for (String table : new String[]{"post_like", "post_bookmark", "post_repost"}) {
                jdbcTemplate.update(
                        "INSERT INTO " + table + " (post_id, account_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                        postId,
                        actorId
                );
            }

            assertThat(jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId)).isEqualTo(1);

            for (String table : new String[]{
                    "post_label", "post_media", "post_like", "post_bookmark", "post_repost"
            }) {
                Integer remainingRows = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + table + " WHERE post_id = ?",
                        Integer.class,
                        postId
                );
                assertThat(remainingRows).as(table + " rows").isZero();
            }
        } finally {
            jdbcTemplate.update("DELETE FROM post WHERE id = ?", postId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id IN (?, ?)", authorId, actorId);
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
                "migration-test-" + accountId
        );
        return accountId;
    }

    private UUID insertPost(UUID authorId, String postType) {
        UUID postId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO post (
                            id, author_id, title, description, post_type, created_at, updated_at
                        ) VALUES (?, ?, NULL, 'Migration constraint target', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                postId,
                authorId,
                postType
        );
        return postId;
    }

    private void insertMedia(UUID postId, int position, String url) {
        jdbcTemplate.update(
                "INSERT INTO post_media (post_id, position, media_type, url) VALUES (?, ?, 'IMAGE', ?)",
                postId,
                position,
                url
        );
    }

    private void assertConstraintViolation(String constraintName, Runnable statement) {
        assertThatThrownBy(statement::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintName);
    }

    private static Stream<Arguments> additionalJavaSpaceCharacters() {
        return Stream.of(
                arguments("U+00A0 NO-BREAK SPACE", "\u00A0"),
                arguments("U+2007 FIGURE SPACE", "\u2007"),
                arguments("U+202F NARROW NO-BREAK SPACE", "\u202F")
        );
    }
}
