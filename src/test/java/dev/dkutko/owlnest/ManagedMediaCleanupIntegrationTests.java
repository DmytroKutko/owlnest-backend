package dev.dkutko.owlnest;

import dev.dkutko.owlnest.media.service.ManagedMediaCleanupService;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import({TestcontainersConfiguration.class, ManagedMediaCleanupIntegrationTests.StorageConfiguration.class})
@SpringBootTest
class ManagedMediaCleanupIntegrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ManagedMediaCleanupService cleanupService;

    @Autowired
    private CleanupRecordingStorage storage;

    private UUID accountId;

    @AfterEach
    void cleanUp() {
        if (accountId != null) {
            jdbcTemplate.update("DELETE FROM managed_media WHERE owner_account_id = ?", accountId);
            jdbcTemplate.update("DELETE FROM identity_account WHERE id = ?", accountId);
        }
        storage.reset();
    }

    @Test
    void claimsDeletesOutsideTransactionFinalizesAndDoesNotDeleteAgain() {
        accountId = insertAccount();
        UUID mediaId = new ManagedMediaTestFixtures(jdbcTemplate).insertDueDeletionPendingAvatar(accountId);

        assertThat(cleanupService.runBatch().deleted()).isEqualTo(1);
        assertThat(storage.calls).singleElement().satisfies(call -> {
            assertThat(call.objectKey()).contains(mediaId.toString());
            assertThat(call.transactionActive()).isFalse();
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM managed_media WHERE id = ?", String.class, mediaId
        )).isEqualTo("DELETED");

        assertThat(cleanupService.runBatch().deleted()).isZero();
        assertThat(storage.calls).hasSize(1);
    }

    @Test
    void clearsLeaseAndSchedulesRetryAfterStorageFailure() {
        accountId = insertAccount();
        UUID mediaId = new ManagedMediaTestFixtures(jdbcTemplate).insertDueDeletionPendingAvatar(accountId);
        storage.unavailable = true;

        assertThat(cleanupService.runBatch().retrying()).isEqualTo(1);

        assertThat(jdbcTemplate.queryForMap(
                """
                        SELECT status, cleanup_lease_token, cleanup_attempt_count, cleanup_last_error_code
                        FROM managed_media WHERE id = ?
                        """,
                mediaId
        )).containsAllEntriesOf(java.util.Map.of(
                "status", "DELETION_PENDING",
                "cleanup_attempt_count", 1,
                "cleanup_last_error_code", "STORAGE_UNAVAILABLE"
        )).containsEntry("cleanup_lease_token", null);
    }

    private UUID insertAccount() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now().minusSeconds(10);
        jdbcTemplate.update(
                """
                        INSERT INTO identity_account (
                            id, provider, external_subject, email, email_verified, created_at, last_seen_at
                        ) VALUES (?, 'KEYCLOAK', ?, ?, false, ?, ?)
                        """,
                id,
                "cleanup-" + id,
                "cleanup-" + id + "@example.com",
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return id;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {

        @Bean
        @Primary
        CleanupRecordingStorage cleanupRecordingStorage() {
            return new CleanupRecordingStorage();
        }
    }

    static final class CleanupRecordingStorage implements MediaObjectStorage {

        private final List<DeleteCall> calls = new ArrayList<>();
        private boolean unavailable;

        @Override
        public PresignedUpload createUploadUrl(UploadUrlRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ObjectMetadata inspect(String objectKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PresignedRead createReadUrl(String objectKey, Instant expiresAt) {
            return new PresignedRead(URI.create("https://unused.invalid"), expiresAt);
        }

        @Override
        public void delete(String objectKey) {
            calls.add(new DeleteCall(objectKey, TransactionSynchronizationManager.isActualTransactionActive()));
            if (unavailable) {
                throw new MediaStorageUnavailableException();
            }
        }

        void reset() {
            calls.clear();
            unavailable = false;
        }
    }

    record DeleteCall(String objectKey, boolean transactionActive) {
    }
}
