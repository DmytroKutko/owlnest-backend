package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
import dev.dkutko.owlnest.media.storage.MediaObjectNotFoundException;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        MediaUploadControllerIntegrationTests.RecordingStorageConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadControllerIntegrationTests {

    private static final String VALID_AVATAR_REQUEST = """
            {
              "purpose": "AVATAR",
              "contentType": "image/webp",
              "sizeBytes": 42
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingMediaObjectStorage storage;

    @BeforeEach
    void resetStorage() {
        storage.reset();
    }

    @Test
    void rejectsEveryUploadLifecycleOperationWithoutAuthenticationAndSideEffects() throws Exception {
        long mediaBefore = managedMediaCount();
        long accountsBefore = accountCount();
        UUID mediaId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/media/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AVATAR_REQUEST))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", mediaId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/media/{mediaId}", mediaId))
                .andExpect(status().isUnauthorized());

        assertThat(managedMediaCount()).isEqualTo(mediaBefore);
        assertThat(accountCount()).isEqualTo(accountsBefore);
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void createsPrivateCreateOnlyUploadOpportunityAndPersistsOwnerReservation() throws Exception {
        String subject = uniqueSubject("create");

        MvcResult result = mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AVATAR_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/media/")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mediaId").isNotEmpty())
                .andExpect(jsonPath("$.purpose").value("AVATAR"))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.sizeBytes").value(42))
                .andExpect(jsonPath("$.state").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.upload.method").value("PUT"))
                .andExpect(jsonPath("$.upload.url").value(startsWith("https://uploads.example.test/")))
                .andExpect(jsonPath("$.upload.requiredHeaders['Content-Type']").value("image/webp"))
                .andExpect(jsonPath("$.upload.requiredHeaders['If-None-Match']").value("*"))
                .andExpect(jsonPath("$.upload.requiredHeaders['Content-Length']").doesNotExist())
                .andExpect(jsonPath("$.upload.expiresAt").isNotEmpty())
                .andReturn();

        UUID mediaId = responseMediaId(result);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo("/api/v1/media/" + mediaId);
        assertThat(storage.ensureAvailableCalls).isEqualTo(1);
        assertThat(storage.uploadRequests).hasSize(1);
        MediaObjectStorage.UploadUrlRequest uploadRequest = storage.uploadRequests.getFirst();
        assertThat(uploadRequest.contentType()).isEqualTo("image/webp");
        assertThat(uploadRequest.contentLength()).isEqualTo(42);
        assertThat(uploadRequest.requireObjectToBeAbsent()).isTrue();

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                """
                        SELECT media.id, media.status, media.purpose,
                               media.declared_content_type, media.declared_size_bytes,
                               media.object_key, account.external_subject
                        FROM managed_media media
                        JOIN identity_account account ON account.id = media.owner_account_id
                        WHERE media.id = ?
                        """,
                mediaId
        );
        assertThat(stored).containsAllEntriesOf(Map.of(
                "id", mediaId,
                "status", "AWAITING_UPLOAD",
                "purpose", "AVATAR",
                "declared_content_type", "image/webp",
                "declared_size_bytes", 42L,
                "object_key", uploadRequest.objectKey(),
                "external_subject", subject
        ));
    }

    @ParameterizedTest(name = "creates exact upload boundary: {0} {1} {2}")
    @MethodSource("validUploadBoundaries")
    void acceptsExactPurposeMimeAndSizeBoundaries(String purpose, String contentType, long sizeBytes)
            throws Exception {
        String subject = uniqueSubject("valid-boundary");

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadRequest(purpose, contentType, sizeBytes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purpose").value(purpose))
                .andExpect(jsonPath("$.contentType").value(contentType))
                .andExpect(jsonPath("$.sizeBytes").value(sizeBytes));

        assertThat(storage.uploadRequests).singleElement().satisfies(request -> {
            assertThat(request.contentType()).isEqualTo(contentType);
            assertThat(request.contentLength()).isEqualTo(sizeBytes);
            assertThat(request.objectKey()).startsWith(
                    purpose.equals("AVATAR") ? "managed/v1/avatars/" : "managed/v1/posts/"
            );
        });
    }

    @ParameterizedTest(name = "rejects invalid upload request: {0}")
    @MethodSource("invalidUploadRequests")
    void rejectsInvalidUploadRequestBeforeStorageOrDatabase(String scenario, String requestBody) throws Exception {
        String subject = uniqueSubject("invalid-request");
        long mediaBefore = managedMediaCount();

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("request.validation_failed"));

        assertThat(managedMediaCount()).isEqualTo(mediaBefore);
        assertThat(accountCountForSubject(subject)).isZero();
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void permitsExactOwnerQuotaAndRejectsNextReservationBeforePresigning() throws Exception {
        String subject = uniqueSubject("quota-boundary");
        UUID accountId = insertAccount(subject);
        for (int index = 0; index < 9; index++) {
            insertAwaitingMedia(
                    accountId,
                    "AVATAR",
                    "image/webp",
                    10L * 1024 * 1024,
                    Instant.now(),
                    Instant.now().plusSeconds(900)
            );
        }

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadRequest("AVATAR", "image/webp", 10L * 1024 * 1024)))
                .andExpect(status().isCreated());
        assertThat(storage.uploadRequests).hasSize(1);

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadRequest("AVATAR", "image/webp", 1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("media.storage_quota_exceeded"));

        assertThat(storage.uploadRequests).hasSize(1);
        assertThat(ownerUsage(accountId)).containsExactly(10L, 100L * 1024 * 1024);
    }

    @Test
    void quotaIsAccountScopedAndDeletedRowsReleaseCapacity() throws Exception {
        String fullSubject = uniqueSubject("quota-full-owner");
        UUID fullAccountId = insertAccount(fullSubject);
        for (int index = 0; index < 10; index++) {
            insertAwaitingMedia(
                    fullAccountId,
                    "AVATAR",
                    "image/jpeg",
                    1,
                    Instant.now(),
                    Instant.now().plusSeconds(900)
            );
        }

        String otherSubject = uniqueSubject("quota-other-owner");
        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(otherSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AVATAR_REQUEST))
                .andExpect(status().isCreated());

        String releasedSubject = uniqueSubject("quota-deleted-release");
        UUID releasedAccountId = insertAccount(releasedSubject);
        for (int index = 0; index < 9; index++) {
            insertAwaitingMedia(
                    releasedAccountId,
                    "AVATAR",
                    "image/jpeg",
                    1,
                    Instant.now(),
                    Instant.now().plusSeconds(900)
            );
        }
        new ManagedMediaTestFixtures(jdbcTemplate).insertDeletedAvatar(releasedAccountId);

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(releasedSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(uploadRequest("AVATAR", "image/jpeg", 1)))
                .andExpect(status().isCreated());
        assertThat(ownerUsage(releasedAccountId).getFirst()).isEqualTo(10L);
    }

    @Test
    void parallelSameAccountReservationsCannotExceedObjectQuota() throws Exception {
        String subject = uniqueSubject("quota-concurrent");
        UUID accountId = insertAccount(subject);
        for (int index = 0; index < 9; index++) {
            insertAwaitingMedia(
                    accountId,
                    "AVATAR",
                    "image/jpeg",
                    1,
                    Instant.now(),
                    Instant.now().plusSeconds(900)
            );
        }
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> request = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/media/uploads")
                                .with(user(subject))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadRequest("AVATAR", "image/jpeg", 1)))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 429);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ownerUsage(accountId).getFirst()).isEqualTo(10L);
        assertThat(storage.uploadRequests).hasSize(1);
    }

    @Test
    void confirmsBodylessUploadAndReturnsIdempotentEstablishedResultWithoutSecondInspect() throws Exception {
        String subject = uniqueSubject("confirm-idempotent");
        CreatedUpload upload = createUpload(subject, VALID_AVATAR_REQUEST);
        storage.putObject(upload.objectKey(), new MediaObjectStorage.ObjectMetadata("image/webp", 42, "etag"));

        MvcResult first = mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.mediaId").value(upload.mediaId().toString()))
                .andExpect(jsonPath("$.purpose").value("AVATAR"))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.sizeBytes").value(42))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty())
                .andReturn();

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().json(first.getResponse().getContentAsString()));

        assertThat(storage.inspectKeys).containsExactly(upload.objectKey());
        Map<String, Object> stored = mediaState(upload.mediaId());
        assertThat(stored.get("status")).isEqualTo("READY");
        assertThat(stored.get("observed_content_type")).isEqualTo("image/webp");
        assertThat(stored.get("observed_size_bytes")).isEqualTo(42L);
        assertThat(stored.get("object_etag")).isEqualTo("etag");
        assertThat(stored.get("ready_at")).isNotNull();
        assertThat(stored.get("ready_expires_at")).isNotNull();
    }

    @Test
    void returnsIncompleteWhenReservedObjectDoesNotExistWithoutChangingState() throws Exception {
        String subject = uniqueSubject("incomplete");
        CreatedUpload upload = createUpload(subject, VALID_AVATAR_REQUEST);

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("media.upload_incomplete"));

        assertThat(storage.inspectKeys).containsExactly(upload.objectKey());
        assertAwaitingUploadWithoutObservedFacts(upload.mediaId());
    }

    @ParameterizedTest(name = "rejects uploaded metadata mismatch: {0}")
    @MethodSource("mismatchedObjectMetadata")
    void returnsMismatchWithoutChangingState(
            String scenario,
            String contentType,
            long contentLength,
            String etag
    ) throws Exception {
        String subject = uniqueSubject("mismatch");
        CreatedUpload upload = createUpload(subject, VALID_AVATAR_REQUEST);
        storage.putObject(
                upload.objectKey(),
                new MediaObjectStorage.ObjectMetadata(contentType, contentLength, etag)
        );

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.upload_mismatch"));

        assertThat(storage.inspectKeys).containsExactly(upload.objectKey());
        assertAwaitingUploadWithoutObservedFacts(upload.mediaId());
    }

    @Test
    void rejectsExpiredUploadBeforeStorageInspectAtExactPersistedBoundary() throws Exception {
        String subject = uniqueSubject("expired");
        UUID accountId = insertAccount(subject);
        Instant now = Instant.now();
        UUID mediaId = insertAwaitingMedia(
                accountId,
                "AVATAR",
                "image/jpeg",
                1,
                now.minus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(1))
        );

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", mediaId)
                        .with(user(subject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.upload_expired"));

        assertThat(storage.inspectKeys).isEmpty();
        assertAwaitingUploadWithoutObservedFacts(mediaId);
    }

    @Test
    void hidesForeignOwnedMediaAsNotFoundWithoutStorageInspectionOrStateChange() throws Exception {
        String owner = uniqueSubject("owner");
        String foreignUser = uniqueSubject("foreign");
        CreatedUpload upload = createUpload(owner, VALID_AVATAR_REQUEST);
        storage.putObject(upload.objectKey(), new MediaObjectStorage.ObjectMetadata("image/webp", 42, "etag"));

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(foreignUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));
        mockMvc.perform(delete("/api/v1/media/{mediaId}", upload.mediaId())
                        .with(user(foreignUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));

        assertThat(storage.inspectKeys).isEmpty();
        assertAwaitingUploadWithoutObservedFacts(upload.mediaId());
    }

    @Test
    void returnsNondisclosingNotFoundForMissingMedia() throws Exception {
        String subject = uniqueSubject("missing");
        UUID missingId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", missingId)
                        .with(user(subject)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));
        mockMvc.perform(delete("/api/v1/media/{mediaId}", missingId)
                        .with(user(subject)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));

        assertThat(storage.inspectKeys).isEmpty();
    }

    @Test
    void returnsSanitizedUnavailableForDisabledCreateAndInspectFailure() throws Exception {
        String disabledSubject = uniqueSubject("disabled");
        long mediaBefore = managedMediaCount();
        storage.available = false;

        mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(disabledSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_AVATAR_REQUEST))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("media.storage_unavailable"))
                .andExpect(jsonPath("$.detail").value("Managed media storage is temporarily unavailable"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("provider")
                )));
        assertThat(managedMediaCount()).isEqualTo(mediaBefore);
        assertThat(storage.ensureAvailableCalls).isEqualTo(1);
        assertThat(storage.uploadRequests).isEmpty();

        storage.reset();
        String confirmSubject = uniqueSubject("inspect-unavailable");
        CreatedUpload upload = createUpload(confirmSubject, VALID_AVATAR_REQUEST);
        storage.inspectUnavailable = true;

        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(confirmSubject)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("media.storage_unavailable"))
                .andExpect(jsonPath("$.detail").value("Managed media storage is temporarily unavailable"));
        assertThat(storage.inspectKeys).containsExactly(upload.objectKey());
        assertAwaitingUploadWithoutObservedFacts(upload.mediaId());
    }

    @Test
    void cancelsPendingMediaWithNoStoreAndSchedulesCleanupThenHidesRepeat() throws Exception {
        String subject = uniqueSubject("cancel-pending");
        CreatedUpload upload = createUpload(subject, VALID_AVATAR_REQUEST);

        mockMvc.perform(delete("/api/v1/media/{mediaId}", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));

        Map<String, Object> state = mediaState(upload.mediaId());
        assertThat(state.get("status")).isEqualTo("DELETION_PENDING");
        assertThat(state.get("deletion_reason")).isEqualTo("USER_CANCELLED");
        assertThat(state.get("deletion_requested_at")).isNotNull();
        assertThat(state.get("cleanup_due_at")).isEqualTo(state.get("cleanup_next_attempt_at"));
        Instant requestedAt = ((Timestamp) state.get("deletion_requested_at")).toInstant();
        Instant cleanupDueAt = ((Timestamp) state.get("cleanup_due_at")).toInstant();
        assertThat(Duration.between(requestedAt, cleanupDueAt)).isEqualTo(Duration.ofHours(24));

        mockMvc.perform(delete("/api/v1/media/{mediaId}", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));
    }

    @Test
    void cancelsReadyMediaWithoutInspectingAgain() throws Exception {
        String subject = uniqueSubject("cancel-ready");
        CreatedUpload upload = createUpload(subject, VALID_AVATAR_REQUEST);
        storage.putObject(upload.objectKey(), new MediaObjectStorage.ObjectMetadata("image/webp", 42, "etag"));
        mockMvc.perform(put("/api/v1/media/{mediaId}/confirmation", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/media/{mediaId}", upload.mediaId())
                        .with(user(subject)))
                .andExpect(status().isNoContent());

        assertThat(storage.inspectKeys).containsExactly(upload.objectKey());
        Map<String, Object> state = mediaState(upload.mediaId());
        assertThat(state.get("status")).isEqualTo("DELETION_PENDING");
        assertThat(state.get("object_etag")).isEqualTo("etag");
    }

    @Test
    void rejectsCancellationOfActiveMediaAsInUseWithoutStateChange() throws Exception {
        String subject = uniqueSubject("active");
        UUID accountId = insertAccount(subject);
        UUID mediaId = insertActiveMedia(accountId);

        mockMvc.perform(delete("/api/v1/media/{mediaId}", mediaId)
                        .with(user(subject)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.in_use"));

        assertThat(mediaState(mediaId).get("status")).isEqualTo("ACTIVE");
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void mapsMalformedMediaIdentifiersToStableValidationProblem() throws Exception {
        String subject = uniqueSubject("malformed-id");

        mockMvc.perform(put("/api/v1/media/not-a-uuid/confirmation")
                        .with(user(subject)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));
        mockMvc.perform(delete("/api/v1/media/not-a-uuid")
                        .with(user(subject)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.validation_failed"));

        assertThat(storage.totalCalls()).isZero();
    }

    private CreatedUpload createUpload(String subject, String requestBody) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/media/uploads")
                        .with(user(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        UUID mediaId = responseMediaId(result);
        MediaObjectStorage.UploadUrlRequest request = storage.uploadRequests.getLast();
        return new CreatedUpload(mediaId, request.objectKey());
    }

    private void assertAwaitingUploadWithoutObservedFacts(UUID mediaId) {
        Map<String, Object> state = mediaState(mediaId);
        assertThat(state.get("status")).isEqualTo("AWAITING_UPLOAD");
        assertThat(state.get("observed_content_type")).isNull();
        assertThat(state.get("observed_size_bytes")).isNull();
        assertThat(state.get("object_etag")).isNull();
        assertThat(state.get("ready_at")).isNull();
    }

    private Map<String, Object> mediaState(UUID mediaId) {
        return jdbcTemplate.queryForMap(
                """
                        SELECT status, observed_content_type, observed_size_bytes, object_etag,
                               ready_at, ready_expires_at, deletion_reason, deletion_requested_at,
                               cleanup_due_at, cleanup_next_attempt_at
                        FROM managed_media
                        WHERE id = ?
                        """,
                mediaId
        );
    }

    private UUID insertAccount(String subject) {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                """
                        INSERT INTO identity_account (
                            id, provider, external_subject, email,
                            email_verified, created_at, last_seen_at
                        )
                        VALUES (?, 'KEYCLOAK', ?, ?, TRUE, ?, ?)
                        """,
                accountId,
                subject,
                subject + "@example.com",
                Timestamp.from(now),
                Timestamp.from(now)
        );
        return accountId;
    }

    private UUID insertAwaitingMedia(
            UUID accountId,
            String purpose,
            String contentType,
            long sizeBytes,
            Instant createdAt,
            Instant uploadExpiresAt
    ) {
        UUID mediaId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            upload_expires_at, cleanup_attempt_count, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, 'AWAITING_UPLOAD', ?, 0, ?, ?)
                        """,
                mediaId,
                accountId,
                purpose,
                "managed/tests/" + mediaId,
                contentType,
                sizeBytes,
                Timestamp.from(uploadExpiresAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
        return mediaId;
    }

    private UUID insertActiveMedia(UUID accountId) {
        Instant createdAt = Instant.now().minus(Duration.ofHours(2));
        Instant readyAt = createdAt.plus(Duration.ofMinutes(10));
        UUID mediaId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO managed_media (
                            id, owner_account_id, purpose, object_key,
                            declared_content_type, declared_size_bytes, status,
                            observed_content_type, observed_size_bytes, object_etag,
                            upload_expires_at, ready_at, ready_expires_at,
                            cleanup_attempt_count, created_at, updated_at
                        )
                        VALUES (?, ?, 'AVATAR', ?, 'image/jpeg', 1, 'ACTIVE',
                                'image/jpeg', 1, 'active-etag', ?, ?, ?, 0, ?, ?)
                        """,
                mediaId,
                accountId,
                "managed/tests/" + mediaId,
                Timestamp.from(createdAt.plus(Duration.ofMinutes(15))),
                Timestamp.from(readyAt),
                Timestamp.from(readyAt.plus(Duration.ofHours(24))),
                Timestamp.from(createdAt),
                Timestamp.from(readyAt)
        );
        return mediaId;
    }

    private long managedMediaCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM managed_media", Long.class);
    }

    private List<Long> ownerUsage(UUID accountId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*), COALESCE(SUM(declared_size_bytes), 0)
                        FROM managed_media
                        WHERE owner_account_id = ? AND status <> 'DELETED'
                        """,
                (resultSet, rowNumber) -> List.of(resultSet.getLong(1), resultSet.getLong(2)),
                accountId
        );
    }

    private long accountCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity_account", Long.class);
    }

    private long accountCountForSubject(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM identity_account WHERE provider = 'KEYCLOAK' AND external_subject = ?",
                Long.class,
                subject
        );
    }

    private static UUID responseMediaId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.mediaId"));
    }

    private static RequestPostProcessor user(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .claim("email", subject + "@example.com")
                .claim("email_verified", true)
        );
    }

    private static String uniqueSubject(String prefix) {
        return "media-" + prefix + "-" + UUID.randomUUID();
    }

    private static String uploadRequest(String purpose, String contentType, long sizeBytes) {
        return """
                {
                  "purpose": "%s",
                  "contentType": "%s",
                  "sizeBytes": %d
                }
                """.formatted(purpose, contentType, sizeBytes);
    }

    private static Stream<Arguments> validUploadBoundaries() {
        return Stream.of(
                arguments("AVATAR", "image/jpeg", 1L),
                arguments("AVATAR", "image/png", 10_485_760L),
                arguments("AVATAR", "image/webp", 1L),
                arguments("POST_IMAGE", "image/jpeg", 1L),
                arguments("POST_IMAGE", "image/png", 20_971_520L),
                arguments("POST_IMAGE", "image/webp", 1L)
        );
    }

    private static Stream<Arguments> invalidUploadRequests() {
        return Stream.of(
                arguments("missing purpose", "{\"contentType\":\"image/jpeg\",\"sizeBytes\":1}"),
                arguments("unknown purpose", uploadRequest("AUDIO", "audio/mpeg", 1)),
                arguments("blank content type", uploadRequest("AVATAR", "   ", 1)),
                arguments("unsupported content type", uploadRequest("AVATAR", "image/gif", 1)),
                arguments("zero size", uploadRequest("AVATAR", "image/jpeg", 0)),
                arguments("avatar above maximum", uploadRequest("AVATAR", "image/jpeg", 10_485_761)),
                arguments("post video deferred", uploadRequest("POST_VIDEO", "video/mp4", 1)),
                arguments("post image above maximum", uploadRequest("POST_IMAGE", "image/png", 20_971_521)),
                arguments("post video above maximum", uploadRequest("POST_VIDEO", "video/mp4", 262_144_001)),
                arguments("malformed JSON", "{\"purpose\":")
        );
    }

    private static Stream<Arguments> mismatchedObjectMetadata() {
        return Stream.of(
                arguments("content type", "image/png", 42L, "etag"),
                arguments("content length", "image/webp", 43L, "etag"),
                arguments("missing etag", "image/webp", 42L, null),
                arguments("blank etag", "image/webp", 42L, "   "),
                arguments("etag above maximum", "image/webp", 42L, "e".repeat(256))
        );
    }

    private record CreatedUpload(UUID mediaId, String objectKey) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingStorageConfiguration {

        @Bean
        @Primary
        RecordingMediaObjectStorage recordingMediaObjectStorage() {
            return new RecordingMediaObjectStorage();
        }
    }

    static class RecordingMediaObjectStorage implements MediaObjectStorage {

        private final List<UploadUrlRequest> uploadRequests = new CopyOnWriteArrayList<>();
        private final List<String> inspectKeys = new CopyOnWriteArrayList<>();
        private final Map<String, ObjectMetadata> objects = new LinkedHashMap<>();
        private int ensureAvailableCalls;
        private boolean available = true;
        private boolean inspectUnavailable;

        @Override
        public void ensureAvailable() {
            ensureAvailableCalls++;
            if (!available) {
                throw new MediaStorageUnavailableException(
                        new IllegalStateException("provider detail must remain private")
                );
            }
        }

        @Override
        public PresignedUpload createUploadUrl(UploadUrlRequest request) {
            uploadRequests.add(request);
            return new PresignedUpload(
                    URI.create("https://uploads.example.test/" + request.objectKey() + "?signature=test"),
                    request.expiresAt(),
                    Map.of(
                            "Content-Type", request.contentType(),
                            "If-None-Match", "*"
                    )
            );
        }

        @Override
        public ObjectMetadata inspect(String objectKey) {
            inspectKeys.add(objectKey);
            if (inspectUnavailable) {
                throw new MediaStorageUnavailableException(
                        new IllegalStateException("provider detail must remain private")
                );
            }
            ObjectMetadata metadata = objects.get(objectKey);
            if (metadata == null) {
                throw new MediaObjectNotFoundException();
            }
            return metadata;
        }

        @Override
        public PresignedRead createReadUrl(String objectKey, Instant expiresAt) {
            throw new UnsupportedOperationException("not used by upload lifecycle tests");
        }

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException("not used by upload lifecycle tests");
        }

        void putObject(String objectKey, ObjectMetadata metadata) {
            objects.put(objectKey, metadata);
        }

        int totalCalls() {
            return ensureAvailableCalls + uploadRequests.size() + inspectKeys.size();
        }

        void reset() {
            uploadRequests.clear();
            inspectKeys.clear();
            objects.clear();
            ensureAvailableCalls = 0;
            available = true;
            inspectUnavailable = false;
        }
    }
}
