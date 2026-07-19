package dev.dkutko.owlnest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        RecordingMediaTestStorageConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
class AvatarControllerIntegrationTests {

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
    void rejectsAvatarAttachAndRemovalWithoutAuthenticationAndSideEffects() throws Exception {
        UUID mediaId = UUID.randomUUID();
        long mediaBefore = mediaCount();
        long accountBefore = accountCount();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/profile/me/avatar"))
                .andExpect(status().isUnauthorized());

        assertThat(mediaCount()).isEqualTo(mediaBefore);
        assertThat(accountCount()).isEqualTo(accountBefore);
        assertThat(storage.totalCalls()).isZero();
    }

    @ParameterizedTest(name = "rejects invalid avatar body: {0}")
    @MethodSource("invalidAvatarBodies")
    void rejectsMissingNullMalformedOrIncompleteAvatarBody(String scenario, String requestBody) throws Exception {
        String subject = uniqueSubject("invalid-avatar-body");
        provisionIncompleteProfile(subject);
        var request = put("/api/v1/profile/me/avatar")
                .with(jwt().jwt(token -> token.subject(subject)))
                .contentType(MediaType.APPLICATION_JSON);
        if (requestBody != null) {
            request.content(requestBody);
        }
        storage.reset();

        mockMvc.perform(request)
                .andExpect(status().isBadRequest());

        assertThat(mediaFixtures.currentAvatarId(accountIdFor(subject))).isNull();
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void mapsMissingAndForeignAvatarCandidatesToSameNondisclosingNotFound() throws Exception {
        String owner = uniqueSubject("avatar-owner");
        String requester = uniqueSubject("avatar-requester");
        provisionIncompleteProfile(owner);
        provisionIncompleteProfile(requester);
        UUID foreignMediaId = mediaFixtures.insertReadyAvatar(accountIdFor(owner));
        UUID missingMediaId = UUID.randomUUID();
        storage.reset();

        String foreignBody = mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(requester)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(foreignMediaId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String missingBody = mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(requester)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(missingMediaId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(foreignBody).isEqualTo(missingBody);
        assertThat(foreignBody).doesNotContain(foreignMediaId.toString(), missingMediaId.toString());
        assertThat(mediaFixtures.mediaRow(foreignMediaId).get("status")).isEqualTo("READY");
        assertThat(mediaFixtures.currentAvatarId(accountIdFor(requester))).isNull();
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void rejectsOwnedReadyPostMediaAsAvatarPurposeMismatch() throws Exception {
        String subject = uniqueSubject("avatar-purpose");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID mediaId = mediaFixtures.insertReadyPostImage(accountId);
        storage.reset();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.purpose_mismatch"));

        assertThat(mediaFixtures.mediaRow(mediaId).get("status")).isEqualTo("READY");
        assertThat(mediaFixtures.currentAvatarId(accountId)).isNull();
        assertThat(storage.totalCalls()).isZero();
    }

    @ParameterizedTest(name = "rejects avatar candidate that is {0}")
    @MethodSource("unattachableAvatarStates")
    void rejectsEveryNonReadyAvatarStateWithStableConflict(AvatarCandidateState state) throws Exception {
        String subject = uniqueSubject("avatar-state-" + state);
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID mediaId = insertCandidate(accountId, state);
        storage.reset();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.not_ready"));

        assertThat(mediaFixtures.currentAvatarId(accountId)).isNull();
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void attachesOwnedReadyAvatarAndReturnsFullCurrentProfileWithPrivateReference() throws Exception {
        String subject = uniqueSubject("avatar-attach");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        String deliveryUrl = "/api/v1/media/" + mediaId + "/delivery";
        storage.reset();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.avatar.mediaId").value(mediaId.toString()))
                .andExpect(jsonPath("$.avatar.deliveryUrl").value(deliveryUrl))
                .andExpect(jsonPath("$.objectKey").doesNotExist());

        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(mediaId);
        assertThat(mediaFixtures.mediaRow(mediaId)).containsEntry("status", "ACTIVE");
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void repeatsSameAvatarAttachmentIdempotentlyWithoutSchedulingCleanup() throws Exception {
        String subject = uniqueSubject("avatar-idempotent");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        attachAvatar(subject, mediaId);
        storage.reset();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.mediaId").value(mediaId.toString()));

        Map<String, Object> row = mediaFixtures.mediaRow(mediaId);
        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(mediaId);
        assertThat(row).containsEntry("status", "ACTIVE");
        assertThat(row.get("deletion_reason")).isNull();
        assertThat(row.get("deletion_requested_at")).isNull();
        assertThat(row.get("cleanup_due_at")).isNull();
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void replacesAvatarAndSchedulesFormerAssetForCleanupExactlyTwentyFourHoursLater() throws Exception {
        String subject = uniqueSubject("avatar-replace");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID formerMediaId = mediaFixtures.insertReadyAvatar(accountId);
        UUID replacementMediaId = mediaFixtures.insertReadyAvatar(accountId);
        attachAvatar(subject, formerMediaId);
        storage.reset();
        Instant before = Instant.now();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(replacementMediaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar.mediaId").value(replacementMediaId.toString()));
        Instant after = Instant.now();

        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(replacementMediaId);
        assertThat(mediaFixtures.mediaRow(replacementMediaId)).containsEntry("status", "ACTIVE");
        assertDetachedState(formerMediaId, "SUPERSEDED", before, after);
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void failedReplacementPreservesFormerCurrentAvatar() throws Exception {
        String subject = uniqueSubject("avatar-failed-replace");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID formerMediaId = mediaFixtures.insertReadyAvatar(accountId);
        UUID expiredReplacementId = mediaFixtures.insertExpiredReadyAvatar(accountId);
        attachAvatar(subject, formerMediaId);
        storage.reset();

        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(expiredReplacementId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("media.not_ready"));

        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(formerMediaId);
        assertThat(mediaFixtures.mediaRow(formerMediaId)).containsEntry("status", "ACTIVE");
        assertThat(mediaFixtures.mediaRow(expiredReplacementId)).containsEntry("status", "READY");
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void removesAvatarAndSchedulesUserRemovedCleanupExactlyTwentyFourHoursLater() throws Exception {
        String subject = uniqueSubject("avatar-remove");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        attachAvatar(subject, mediaId);
        storage.reset();
        Instant before = Instant.now();

        mockMvc.perform(delete("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        Instant after = Instant.now();

        assertThat(mediaFixtures.currentAvatarId(accountId)).isNull();
        assertDetachedState(mediaId, "USER_REMOVED", before, after);
        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void removingAbsentAvatarIsNoContentNoOp() throws Exception {
        String subject = uniqueSubject("avatar-absent-remove");
        provisionIncompleteProfile(subject);
        UUID accountId = accountIdFor(subject);
        storage.reset();

        mockMvc.perform(delete("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(mediaFixtures.currentAvatarId(accountId)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM managed_media WHERE owner_account_id = ?",
                Long.class,
                accountId
        )).isZero();
        assertThat(storage.totalCalls()).isZero();
    }

    private void assertDetachedState(
            UUID mediaId,
            String reason,
            Instant before,
            Instant after
    ) {
        Map<String, Object> row = mediaFixtures.mediaRow(mediaId);
        Instant requestedAt = ((Timestamp) row.get("deletion_requested_at")).toInstant();
        Instant cleanupDueAt = ((Timestamp) row.get("cleanup_due_at")).toInstant();
        assertThat(row)
                .containsEntry("status", "DELETION_PENDING")
                .containsEntry("deletion_reason", reason)
                .containsEntry("cleanup_next_attempt_at", row.get("cleanup_due_at"));
        assertThat(requestedAt).isBetween(before.minusMillis(1), after.plusMillis(1));
        assertThat(Duration.between(requestedAt, cleanupDueAt)).isEqualTo(DETACHED_RETENTION);
    }

    private UUID insertCandidate(UUID accountId, AvatarCandidateState state) {
        return switch (state) {
            case AWAITING_UPLOAD -> mediaFixtures.insertAwaitingAvatar(accountId);
            case EXPIRED_READY -> mediaFixtures.insertExpiredReadyAvatar(accountId);
            case DELETION_PENDING -> mediaFixtures.insertDeletionPendingAvatar(accountId);
            case DELETED -> mediaFixtures.insertDeletedAvatar(accountId);
        };
    }

    private void attachAvatar(String subject, UUID mediaId) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me/avatar")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(avatarRequest(mediaId)))
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

    private long mediaCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM managed_media", Long.class);
    }

    private long accountCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM identity_account", Long.class);
    }

    private static String avatarRequest(UUID mediaId) {
        return "{\"mediaId\":\"" + mediaId + "\"}";
    }

    private static String uniqueSubject(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static Stream<String[]> invalidAvatarBodies() {
        return Stream.of(
                new String[]{"missing body", null},
                new String[]{"JSON null", "null"},
                new String[]{"malformed JSON", "{\"mediaId\":"},
                new String[]{"missing mediaId", "{}"},
                new String[]{"null mediaId", "{\"mediaId\":null}"}
        );
    }

    private static Stream<AvatarCandidateState> unattachableAvatarStates() {
        return Stream.of(
                AvatarCandidateState.AWAITING_UPLOAD,
                AvatarCandidateState.EXPIRED_READY,
                AvatarCandidateState.DELETION_PENDING,
                AvatarCandidateState.DELETED
        );
    }

    private enum AvatarCandidateState {
        AWAITING_UPLOAD,
        EXPIRED_READY,
        DELETION_PENDING,
        DELETED
    }
}
