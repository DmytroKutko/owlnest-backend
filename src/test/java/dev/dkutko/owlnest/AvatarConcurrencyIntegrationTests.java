package dev.dkutko.owlnest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        RecordingMediaTestStorageConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
class AvatarConcurrencyIntegrationTests {

    private static final Duration DETACHED_RETENTION = Duration.ofHours(24);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecordingMediaTestStorage storage;

    private ManagedMediaTestFixtures mediaFixtures;

    @BeforeEach
    void setUp() {
        storage.reset();
        mediaFixtures = new ManagedMediaTestFixtures(jdbcTemplate);
    }

    @Test
    void simultaneousDifferentReadyReplacementsSerializeWithoutDeadlockAndLeaveOneCurrentAvatar()
            throws Exception {
        for (int iteration = 0; iteration < 3; iteration++) {
            String subject = uniqueSubject("concurrent-avatar-replacement-" + iteration);
            provisionIncompleteProfile(subject);
            UUID accountId = accountIdFor(subject);
            UUID formerMediaId = mediaFixtures.insertReadyAvatar(accountId);
            UUID firstCandidateId = mediaFixtures.insertReadyAvatar(accountId);
            UUID secondCandidateId = mediaFixtures.insertReadyAvatar(accountId);
            attachAvatar(subject, formerMediaId);
            storage.reset();
            CyclicBarrier barrier = new CyclicBarrier(2);

            List<Integer> statuses = executeConcurrently(
                    avatarRequest(subject, firstCandidateId, barrier),
                    avatarRequest(subject, secondCandidateId, barrier)
            );

            assertThat(statuses).containsExactlyInAnyOrder(200, 200);
            UUID currentMediaId = mediaFixtures.currentAvatarId(accountId);
            assertThat(currentMediaId).isIn(firstCandidateId, secondCandidateId);
            UUID losingCandidateId = currentMediaId.equals(firstCandidateId)
                    ? secondCandidateId
                    : firstCandidateId;
            assertCurrentActiveInvariant(accountId, currentMediaId);
            assertSuperseded(formerMediaId);
            assertSuperseded(losingCandidateId);
            assertThat(storage.totalCalls()).isZero();
        }
    }

    @Test
    void simultaneousSameCandidateAttachmentIsIdempotentAndDoesNotScheduleCandidateCleanup()
            throws Exception {
        String subject = uniqueSubject("concurrent-same-avatar");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID candidateId = mediaFixtures.insertReadyAvatar(accountId);
        storage.reset();
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Integer> statuses = executeConcurrently(
                avatarRequest(subject, candidateId, barrier),
                avatarRequest(subject, candidateId, barrier)
        );

        assertThat(statuses).containsExactlyInAnyOrder(200, 200);
        assertCurrentActiveInvariant(accountId, candidateId);
        Map<String, Object> candidate = mediaFixtures.mediaRow(candidateId);
        assertThat(candidate.get("deletion_reason")).isNull();
        assertThat(candidate.get("deletion_requested_at")).isNull();
        assertThat(candidate.get("cleanup_due_at")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM managed_media WHERE owner_account_id = ?",
                Long.class,
                accountId
        )).isEqualTo(1L);
        assertThat(storage.totalCalls()).isZero();
    }

    private List<Integer> executeConcurrently(
            Callable<Integer> firstRequest,
            Callable<Integer> secondRequest
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(firstRequest);
            Future<Integer> second = executor.submit(secondRequest);
            return List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent avatar executor did not terminate");
            }
        }
    }

    private Callable<Integer> avatarRequest(String subject, UUID mediaId, CyclicBarrier barrier) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            return mockMvc.perform(put("/api/v1/profile/me/avatar")
                            .with(jwt().jwt(token -> token.subject(subject)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mediaId\":\"" + mediaId + "\"}"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };
    }

    private void assertCurrentActiveInvariant(UUID accountId, UUID currentMediaId) {
        assertThat(mediaFixtures.mediaRow(currentMediaId))
                .containsEntry("owner_account_id", accountId)
                .containsEntry("purpose", "AVATAR")
                .containsEntry("status", "ACTIVE");
        assertThat(mediaFixtures.mediaRow(currentMediaId).get("deletion_reason")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM profile profile
                        JOIN managed_media media ON media.id = profile.avatar_media_id
                        WHERE profile.account_id = ?
                          AND media.owner_account_id = profile.account_id
                          AND media.purpose = 'AVATAR'
                          AND media.status = 'ACTIVE'
                        """,
                Long.class,
                accountId
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM managed_media WHERE owner_account_id = ? AND status = 'ACTIVE'",
                Long.class,
                accountId
        )).isEqualTo(1L);
    }

    private void assertSuperseded(UUID mediaId) {
        Map<String, Object> row = mediaFixtures.mediaRow(mediaId);
        Instant requestedAt = ((Timestamp) row.get("deletion_requested_at")).toInstant();
        Instant cleanupDueAt = ((Timestamp) row.get("cleanup_due_at")).toInstant();
        assertThat(row)
                .containsEntry("status", "DELETION_PENDING")
                .containsEntry("deletion_reason", "SUPERSEDED")
                .containsEntry("cleanup_next_attempt_at", row.get("cleanup_due_at"));
        assertThat(Duration.between(requestedAt, cleanupDueAt)).isEqualTo(DETACHED_RETENTION);
    }

    private void attachAvatar(String subject, UUID mediaId) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mediaId\":\"" + mediaId + "\"}"))
                .andExpect(status().isOk());
    }

    private void provisionIncompleteProfile(String subject) throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk());
    }

    private UUID accountIdFor(String subject) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM identity_account WHERE external_subject = ?",
                UUID.class,
                subject
        );
    }

    private static String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

}
