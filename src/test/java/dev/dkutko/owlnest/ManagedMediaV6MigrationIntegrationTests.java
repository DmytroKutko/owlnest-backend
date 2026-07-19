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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ManagedMediaV6MigrationIntegrationTests {

    private static final Instant BASE_TIME = Instant.parse("2026-07-18T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Test
    void appliesV6WithExpectedCatalogConstraintsGeneratedColumnsIndexesAndTrigger() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT success FROM flyway_schema_history WHERE version = '6'",
                Boolean.class
        )).isTrue();

        Map<String, String> columns = jdbcTemplate.query(
                """
                        SELECT column_name,
                               data_type,
                               is_nullable,
                               is_generated
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'managed_media'
                        ORDER BY ordinal_position
                        """,
                resultSet -> {
                    Map<String, String> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(
                                resultSet.getString("column_name"),
                                resultSet.getString("data_type")
                                        + "|" + resultSet.getString("is_nullable")
                                        + "|" + resultSet.getString("is_generated")
                        );
                    }
                    return result;
                }
        );
        assertThat(columns).containsAllEntriesOf(Map.ofEntries(
                Map.entry("id", "uuid|NO|NEVER"),
                Map.entry("owner_account_id", "uuid|NO|NEVER"),
                Map.entry("purpose", "character varying|NO|NEVER"),
                Map.entry("object_key", "character varying|NO|NEVER"),
                Map.entry("declared_content_type", "character varying|NO|NEVER"),
                Map.entry("declared_size_bytes", "bigint|NO|NEVER"),
                Map.entry("status", "character varying|NO|NEVER"),
                Map.entry("observed_content_type", "character varying|YES|NEVER"),
                Map.entry("observed_size_bytes", "bigint|YES|NEVER"),
                Map.entry("object_etag", "character varying|YES|NEVER"),
                Map.entry("upload_expires_at", "timestamp with time zone|NO|NEVER"),
                Map.entry("ready_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("ready_expires_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("deletion_reason", "character varying|YES|NEVER"),
                Map.entry("deletion_requested_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("cleanup_due_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("cleanup_lease_token", "uuid|YES|NEVER"),
                Map.entry("cleanup_lease_expires_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("cleanup_attempt_count", "integer|NO|NEVER"),
                Map.entry("cleanup_next_attempt_at", "timestamp with time zone|YES|NEVER"),
                Map.entry("cleanup_last_error_code", "character varying|YES|NEVER"),
                Map.entry("created_at", "timestamp with time zone|NO|NEVER"),
                Map.entry("updated_at", "timestamp with time zone|NO|NEVER"),
                Map.entry("deleted_at", "timestamp with time zone|YES|NEVER")
        ));

        Map<String, String> generatedColumns = jdbcTemplate.query(
                """
                        SELECT table_name, column_name, generation_expression
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND (table_name, column_name) = ('profile', 'avatar_media_purpose')
                        ORDER BY table_name
                        """,
                resultSet -> {
                    Map<String, String> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(
                                resultSet.getString("table_name") + "." + resultSet.getString("column_name"),
                                resultSet.getString("generation_expression")
                        );
                    }
                    return result;
                }
        );
        assertThat(generatedColumns).containsOnlyKeys("profile.avatar_media_purpose");
        assertThat(generatedColumns.get("profile.avatar_media_purpose")).contains("AVATAR");

        Set<String> constraints = Set.copyOf(jdbcTemplate.queryForList(
                """
                        SELECT conname
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname IN (
                              'pk_managed_media',
                              'uq_managed_media_object_key',
                              'uq_managed_media_id_purpose',
                              'fk_managed_media_owner',
                              'ck_managed_media_purpose',
                              'ck_managed_media_declared_metadata',
                              'ck_managed_media_status',
                              'ck_managed_media_object_key',
                              'ck_managed_media_observed_metadata',
                              'ck_managed_media_ready_window',
                              'ck_managed_media_deletion_reason',
                              'ck_managed_media_cleanup_lease',
                              'ck_managed_media_cleanup_attempt_count',
                              'ck_managed_media_timestamps',
                              'ck_managed_media_lifecycle',
                              'uq_profile_avatar_media_id',
                              'fk_profile_avatar_media'
                          )
                        """,
                String.class
        ));
        assertThat(constraints).containsExactlyInAnyOrder(
                "pk_managed_media",
                "uq_managed_media_object_key",
                "uq_managed_media_id_purpose",
                "fk_managed_media_owner",
                "ck_managed_media_purpose",
                "ck_managed_media_declared_metadata",
                "ck_managed_media_status",
                "ck_managed_media_object_key",
                "ck_managed_media_observed_metadata",
                "ck_managed_media_ready_window",
                "ck_managed_media_deletion_reason",
                "ck_managed_media_cleanup_lease",
                "ck_managed_media_cleanup_attempt_count",
                "ck_managed_media_timestamps",
                "ck_managed_media_lifecycle",
                "uq_profile_avatar_media_id",
                "fk_profile_avatar_media"
        );

        Map<String, Boolean> validatedConstraints = jdbcTemplate.query(
                """
                        SELECT conname, convalidated
                        FROM pg_constraint
                        WHERE connamespace = current_schema()::regnamespace
                          AND conname = 'fk_profile_avatar_media'
                        """,
                resultSet -> {
                    Map<String, Boolean> result = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        result.put(resultSet.getString("conname"), resultSet.getBoolean("convalidated"));
                    }
                    return result;
                }
        );
        assertThat(validatedConstraints).containsOnly(Map.entry("fk_profile_avatar_media", true));

        Set<String> indexes = jdbcTemplate.queryForList(
                        """
                                SELECT indexname
                                FROM pg_indexes
                                WHERE schemaname = current_schema()
                                  AND tablename IN ('managed_media', 'profile', 'post_media')
                                """,
                        String.class
                ).stream()
                .collect(Collectors.toSet());
        assertThat(indexes).contains(
                "pk_managed_media",
                "uq_managed_media_object_key",
                "uq_managed_media_id_purpose",
                "idx_managed_media_owner_status",
                "idx_managed_media_awaiting_upload_expiry",
                "idx_managed_media_ready_expiry",
                "idx_managed_media_cleanup_due",
                "uq_profile_avatar_media_id"
        );

        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM pg_trigger trigger_definition
                        JOIN pg_class table_definition
                          ON table_definition.oid = trigger_definition.tgrelid
                        JOIN pg_namespace namespace
                          ON namespace.oid = table_definition.relnamespace
                        WHERE namespace.nspname = current_schema()
                          AND table_definition.relname = 'managed_media'
                          AND trigger_definition.tgname = 'trg_managed_media_immutable_facts'
                          AND NOT trigger_definition.tgisinternal
                          AND trigger_definition.tgenabled = 'O'
                        """,
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void upgradesPopulatedV5WithoutChangingLegacyPostMediaAndKeepsOldInsertCompatible() {
        String schema = "media_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("5"))
                    .load()
                    .migrate();

            UUID accountId = insertAccount(upgradeJdbc, schema);
            UUID postId = insertPost(upgradeJdbc, schema, accountId);
            List<LegacyMediaRow> expectedLegacyRows = List.of(
                    legacyRow(postId, 0, "IMAGE", "https://cdn.example.com/legacy-image.png?x=1#preview"),
                    legacyRow(postId, 1, "VIDEO", "https://cdn.example.com/legacy-video.mp4")
            );
            for (LegacyMediaRow row : expectedLegacyRows) {
                upgradeJdbc.update(
                        "INSERT INTO " + schema
                                + ".post_media (post_id, position, media_type, url) VALUES (?, ?, ?, ?)",
                        row.postId(),
                        row.position(),
                        row.mediaType(),
                        row.url()
                );
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertThat(upgradeJdbc.queryForObject(
                    "SELECT success FROM " + schema + ".flyway_schema_history WHERE version = '6'",
                    Boolean.class
            )).isTrue();
            assertThat(upgradeJdbc.query(
                    """
                            SELECT post_id, position, media_type, url
                            FROM %s.post_media
                            WHERE post_id = ?
                            ORDER BY position
                            """.formatted(schema),
                    (resultSet, rowNumber) -> new LegacyMediaRow(
                            resultSet.getObject("post_id", UUID.class),
                            resultSet.getInt("position"),
                            resultSet.getString("media_type"),
                            resultSet.getString("url")
                    ),
                    postId
            )).containsExactlyElementsOf(expectedLegacyRows);
            assertThat(upgradeJdbc.queryForList(
                    """
                            SELECT column_name
                            FROM information_schema.columns
                            WHERE table_schema = ? AND table_name = 'post_media'
                            ORDER BY ordinal_position
                            """,
                    String.class,
                    schema
            )).containsExactly("post_id", "position", "media_type", "url");

            upgradeJdbc.update(
                    "INSERT INTO " + schema
                            + ".post_media (post_id, position, media_type, url) VALUES (?, 2, 'IMAGE', ?)",
                    postId,
                    "https://cdn.example.com/legacy-after-v6.webp"
            );
            assertThat(upgradeJdbc.queryForObject(
                    "SELECT url FROM " + schema + ".post_media WHERE post_id = ? AND position = 2",
                    String.class,
                    postId
            )).isEqualTo("https://cdn.example.com/legacy-after-v6.webp");

            assertThat(upgradeJdbc.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = ? AND table_name = 'post_media' AND column_name = 'url'",
                    String.class,
                    schema
            )).isEqualTo("NO");
        } finally {
            upgradeJdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void enforcesNullTotalObservedMetadataAndReadyWindow() {
        UUID accountId = insertAccount(jdbcTemplate, null);
        try {
            assertDataIntegrityViolation(() -> insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "AWAITING_UPLOAD",
                    "image/png",
                    null,
                    null,
                    null,
                    null,
                    null
            ));
            assertDataIntegrityViolation(() -> insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "AWAITING_UPLOAD",
                    "image/png",
                    5L,
                    null,
                    null,
                    null,
                    null
            ));
            assertDataIntegrityViolation(() -> insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "READY",
                    "image/png",
                    5L,
                    "etag",
                    BASE_TIME.plusSeconds(1),
                    null,
                    null
            ));
            assertDataIntegrityViolation(() -> insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "READY",
                    "image/png",
                    5L,
                    "etag",
                    null,
                    BASE_TIME.plusSeconds(2),
                    null
            ));
            assertDataIntegrityViolation(() -> insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "READY",
                    "image/png",
                    5L,
                    "etag",
                    BASE_TIME.plusSeconds(2),
                    BASE_TIME.plusSeconds(2),
                    null
            ));

            UUID readyId = insertMediaRow(
                    accountId,
                    "POST_IMAGE",
                    "image/png",
                    5,
                    "READY",
                    "image/png",
                    5L,
                    "etag",
                    BASE_TIME.plusSeconds(1),
                    BASE_TIME.plusSeconds(86_400),
                    null
            );
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM managed_media WHERE id = ?",
                    String.class,
                    readyId
            )).isEqualTo("READY");
        } finally {
            jdbcTemplate.update("DELETE FROM managed_media WHERE owner_account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    @Test
    void preventsDirectMutationOfReservationAndOnceSetConfirmationFacts() {
        UUID accountId = insertAccount(jdbcTemplate, null);
        UUID replacementOwnerId = insertAccount(jdbcTemplate, null);
        UUID mediaId = insertReadyMedia(accountId, "AVATAR", "image/jpeg", 5);
        try {
            Map<String, Object> immutableMutations = new LinkedHashMap<>();
            immutableMutations.put("id", UUID.randomUUID());
            immutableMutations.put("owner_account_id", replacementOwnerId);
            immutableMutations.put("purpose", "POST_IMAGE");
            immutableMutations.put("object_key", "managed/replaced-object-key");
            immutableMutations.put("declared_content_type", "image/png");
            immutableMutations.put("declared_size_bytes", 6L);
            immutableMutations.put("upload_expires_at", Timestamp.from(BASE_TIME.plusSeconds(1_000)));
            immutableMutations.put("created_at", Timestamp.from(BASE_TIME.minusSeconds(1)));
            immutableMutations.put("observed_content_type", "image/png");
            immutableMutations.put("observed_size_bytes", 6L);
            immutableMutations.put("object_etag", "different-etag");
            immutableMutations.put("ready_at", Timestamp.from(BASE_TIME.plusSeconds(2)));
            immutableMutations.put("ready_expires_at", Timestamp.from(BASE_TIME.plusSeconds(90_000)));

            immutableMutations.forEach((column, value) -> assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE managed_media SET " + column + " = ? WHERE id = ?",
                    value,
                    mediaId
            ))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("immutable"));

            jdbcTemplate.update(
                    "UPDATE managed_media SET status = 'ACTIVE', updated_at = ? WHERE id = ?",
                    Timestamp.from(BASE_TIME.plusSeconds(2)),
                    mediaId
            );
            jdbcTemplate.update(
                    """
                            UPDATE managed_media
                            SET status = 'DELETION_PENDING',
                                deletion_reason = 'DETACHED',
                                deletion_requested_at = ?,
                                cleanup_due_at = ?,
                                cleanup_next_attempt_at = ?,
                                updated_at = ?
                            WHERE id = ?
                            """,
                    Timestamp.from(BASE_TIME.plusSeconds(3)),
                    Timestamp.from(BASE_TIME.plusSeconds(4)),
                    Timestamp.from(BASE_TIME.plusSeconds(4)),
                    Timestamp.from(BASE_TIME.plusSeconds(3)),
                    mediaId
            );
            UUID leaseToken = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                            UPDATE managed_media
                            SET cleanup_lease_token = ?,
                                cleanup_lease_expires_at = ?,
                                cleanup_attempt_count = 1,
                                cleanup_next_attempt_at = ?,
                                cleanup_last_error_code = 'R2_TIMEOUT',
                                updated_at = ?
                            WHERE id = ?
                            """,
                    leaseToken,
                    Timestamp.from(BASE_TIME.plusSeconds(34)),
                    Timestamp.from(BASE_TIME.plusSeconds(64)),
                    Timestamp.from(BASE_TIME.plusSeconds(5)),
                    mediaId
            );
            assertThat(jdbcTemplate.queryForMap(
                    """
                            SELECT status, deletion_reason, cleanup_attempt_count,
                                   cleanup_lease_token, cleanup_last_error_code
                            FROM managed_media
                            WHERE id = ?
                            """,
                    mediaId
            )).containsAllEntriesOf(Map.of(
                    "status", "DELETION_PENDING",
                    "deletion_reason", "DETACHED",
                    "cleanup_attempt_count", 1,
                    "cleanup_lease_token", leaseToken,
                    "cleanup_last_error_code", "R2_TIMEOUT"
            ));
        } finally {
            jdbcTemplate.update("DELETE FROM managed_media WHERE id = ?", mediaId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id IN (?, ?)", accountId, replacementOwnerId);
        }
    }

    @Test
    void allowsCanonicalLifecycleAndEnforcesDeletionReasonShape() {
        UUID accountId = insertAccount(jdbcTemplate, null);
        UUID mediaId = insertAwaitingMedia(accountId, "POST_VIDEO", "video/mp4", 5);
        try {
            assertConstraintViolation(
                    "ck_managed_media_lifecycle",
                    () -> jdbcTemplate.update(
                            "UPDATE managed_media SET deletion_reason = 'DETACHED' WHERE id = ?",
                            mediaId
                    )
            );
            assertConstraintViolation(
                    "ck_managed_media_deletion_reason",
                    () -> jdbcTemplate.update(
                            """
                                    UPDATE managed_media
                                    SET status = 'DELETION_PENDING',
                                        deletion_reason = 'UNKNOWN_REASON',
                                        deletion_requested_at = ?,
                                        cleanup_due_at = ?,
                                        cleanup_next_attempt_at = ?,
                                        updated_at = ?
                                    WHERE id = ?
                                    """,
                            Timestamp.from(BASE_TIME.plusSeconds(1)),
                            Timestamp.from(BASE_TIME.plusSeconds(2)),
                            Timestamp.from(BASE_TIME.plusSeconds(2)),
                            Timestamp.from(BASE_TIME.plusSeconds(1)),
                            mediaId
                    )
            );

            jdbcTemplate.update(
                    """
                            UPDATE managed_media
                            SET status = 'READY',
                                observed_content_type = declared_content_type,
                                observed_size_bytes = declared_size_bytes,
                                object_etag = 'video-etag',
                                ready_at = ?,
                                ready_expires_at = ?,
                                updated_at = ?
                            WHERE id = ?
                            """,
                    Timestamp.from(BASE_TIME.plusSeconds(1)),
                    Timestamp.from(BASE_TIME.plusSeconds(86_400)),
                    Timestamp.from(BASE_TIME.plusSeconds(1)),
                    mediaId
            );
            jdbcTemplate.update(
                    "UPDATE managed_media SET status = 'ACTIVE', updated_at = ? WHERE id = ?",
                    Timestamp.from(BASE_TIME.plusSeconds(2)),
                    mediaId
            );
            jdbcTemplate.update(
                    """
                            UPDATE managed_media
                            SET status = 'DELETION_PENDING',
                                deletion_reason = 'DETACHED',
                                deletion_requested_at = ?,
                                cleanup_due_at = ?,
                                cleanup_next_attempt_at = ?,
                                updated_at = ?
                            WHERE id = ?
                            """,
                    Timestamp.from(BASE_TIME.plusSeconds(3)),
                    Timestamp.from(BASE_TIME.plusSeconds(4)),
                    Timestamp.from(BASE_TIME.plusSeconds(4)),
                    Timestamp.from(BASE_TIME.plusSeconds(3)),
                    mediaId
            );
            jdbcTemplate.update(
                    """
                            UPDATE managed_media
                            SET status = 'DELETED',
                                cleanup_attempt_count = 1,
                                cleanup_next_attempt_at = NULL,
                                cleanup_last_error_code = NULL,
                                cleanup_lease_token = NULL,
                                cleanup_lease_expires_at = NULL,
                                deleted_at = ?,
                                updated_at = ?
                            WHERE id = ?
                            """,
                    Timestamp.from(BASE_TIME.plusSeconds(5)),
                    Timestamp.from(BASE_TIME.plusSeconds(5)),
                    mediaId
            );
            assertThat(jdbcTemplate.queryForMap(
                    "SELECT status, deletion_reason, cleanup_attempt_count FROM managed_media WHERE id = ?",
                    mediaId
            )).containsAllEntriesOf(Map.of(
                    "status", "DELETED",
                    "deletion_reason", "DETACHED",
                    "cleanup_attempt_count", 1
            ));

            assertAllowedDeletionReason(accountId, "UPLOAD_EXPIRED", false);
            assertAllowedDeletionReason(accountId, "READY_EXPIRED", true);
            assertAllowedDeletionReason(accountId, "SUPERSEDED", true);
            assertAllowedDeletionReason(accountId, "DETACHED", true);
            assertAllowedDeletionReason(accountId, "USER_REMOVED", true);
            assertAllowedDeletionReason(accountId, "USER_CANCELLED", false);
        } finally {
            jdbcTemplate.update("DELETE FROM managed_media WHERE owner_account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
    }

    private void assertAllowedDeletionReason(UUID accountId, String deletionReason, boolean confirmed) {
        UUID candidateId = confirmed
                ? insertReadyMedia(accountId, "POST_IMAGE", "image/jpeg", 1)
                : insertAwaitingMedia(accountId, "POST_IMAGE", "image/jpeg", 1);
        Instant requestedAt = BASE_TIME.plusSeconds(10);
        jdbcTemplate.update(
                """
                        UPDATE managed_media
                        SET status = 'DELETION_PENDING',
                            deletion_reason = ?,
                            deletion_requested_at = ?,
                            cleanup_due_at = ?,
                            cleanup_next_attempt_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                deletionReason,
                Timestamp.from(requestedAt),
                Timestamp.from(requestedAt.plusSeconds(1)),
                Timestamp.from(requestedAt.plusSeconds(1)),
                Timestamp.from(requestedAt),
                candidateId
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deletion_reason FROM managed_media WHERE id = ?",
                String.class,
                candidateId
        )).isEqualTo(deletionReason);
    }

    private UUID insertReadyMedia(UUID accountId, String purpose, String contentType, long sizeBytes) {
        return insertMediaRow(
                accountId,
                purpose,
                contentType,
                sizeBytes,
                "READY",
                contentType,
                sizeBytes,
                "etag-" + UUID.randomUUID(),
                BASE_TIME.plusSeconds(1),
                BASE_TIME.plusSeconds(86_400),
                null
        );
    }

    private UUID insertAwaitingMedia(UUID accountId, String purpose, String contentType, long sizeBytes) {
        return insertMediaRow(
                accountId,
                purpose,
                contentType,
                sizeBytes,
                "AWAITING_UPLOAD",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private UUID insertMediaRow(
            UUID accountId,
            String purpose,
            String contentType,
            long sizeBytes,
            String status,
            String observedContentType,
            Long observedSizeBytes,
            String objectEtag,
            Instant readyAt,
            Instant readyExpiresAt,
            String deletionReason
    ) {
        UUID mediaId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            observed_content_type, observed_size_bytes, object_etag,
                            upload_expires_at, ready_at, ready_expires_at, deletion_reason,
                            cleanup_attempt_count, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                        """,
                mediaId,
                accountId,
                purpose,
                "managed/tests/" + mediaId,
                contentType,
                sizeBytes,
                status,
                observedContentType,
                observedSizeBytes,
                objectEtag,
                Timestamp.from(BASE_TIME.plusSeconds(900)),
                readyAt == null ? null : Timestamp.from(readyAt),
                readyExpiresAt == null ? null : Timestamp.from(readyExpiresAt),
                deletionReason,
                Timestamp.from(BASE_TIME),
                Timestamp.from(readyAt == null ? BASE_TIME : readyAt)
        );
        return mediaId;
    }

    private UUID insertAccount(JdbcTemplate jdbc, String schema) {
        UUID accountId = UUID.randomUUID();
        String table = qualified(schema, "identity_account");
        jdbc.update(
                """
                        INSERT INTO %s (
                            id, provider, external_subject, email,
                            email_verified, created_at, last_seen_at
                        )
                        VALUES (?, 'keycloak', ?, ?, TRUE, ?, ?)
                        """.formatted(table),
                accountId,
                "media-subject-" + accountId,
                accountId + "@example.com",
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME)
        );
        return accountId;
    }

    private UUID insertPost(JdbcTemplate jdbc, String schema, UUID authorId) {
        UUID postId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO %s (
                            id, author_id, title, description, post_type,
                            like_count, comment_count, repost_count,
                            created_at, updated_at, deleted_at
                        )
                        VALUES (?, ?, 'Media migration', 'Legacy media migration test', 'PERSONAL',
                                0, 0, 0, ?, ?, NULL)
                        """.formatted(qualified(schema, "post")),
                postId,
                authorId,
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME)
        );
        return postId;
    }

    private void insertProfile(UUID accountId) {
        jdbcTemplate.update(
                """
                        INSERT INTO profile (
                            account_id, username, display_name, bio,
                            created_at, updated_at, onboarding_completed
                        )
                        VALUES (?, ?, 'Media Owner', NULL, ?, ?, TRUE)
                        """,
                accountId,
                "media_" + accountId.toString().replace("-", "").substring(0, 20),
                Timestamp.from(BASE_TIME),
                Timestamp.from(BASE_TIME)
        );
    }

    private static LegacyMediaRow legacyRow(
            UUID postId,
            int position,
            String mediaType,
            String url
    ) {
        return new LegacyMediaRow(postId, position, mediaType, url);
    }

    private static String qualified(String schema, String table) {
        return schema == null ? table : schema + "." + table;
    }

    private static void assertConstraintViolation(String constraint, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraint);
    }

    private static void assertDataIntegrityViolation(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);
    }

    private record LegacyMediaRow(UUID postId, int position, String mediaType, String url) {
    }
}
