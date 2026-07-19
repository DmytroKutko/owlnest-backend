package dev.dkutko.owlnest;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({
        TestcontainersConfiguration.class,
        RecordingMediaTestStorageConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
class ManagedMediaDeliveryIntegrationTests {

    private static final Duration READ_CAPABILITY_TTL = Duration.ofMinutes(5);

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
    void rejectsDeliveryWithoutAuthenticationBeforeStorageAccess() throws Exception {
        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void ownerCanDeliverActiveAvatarBeforeCompletingProfile() throws Exception {
        String owner = uniqueSubject("delivery-incomplete-owner");
        provisionIncompleteProfile(owner);
        UUID accountId = accountIdFor(owner);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        mediaFixtures.activateAvatar(accountId, mediaId);
        storage.reset();

        assertSuccessfulDelivery(owner, mediaId);
    }

    @Test
    void foreignAuthenticatedViewerCanDeliverCompletedPublicProfileAvatar() throws Exception {
        String owner = uniqueSubject("delivery-completed-owner");
        String viewer = uniqueSubject("delivery-completed-viewer");
        completeProfile(owner);
        provisionIncompleteProfile(viewer);
        UUID accountId = accountIdFor(owner);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        mediaFixtures.activateAvatar(accountId, mediaId);
        storage.reset();

        assertSuccessfulDelivery(viewer, mediaId);
    }

    @Test
    void foreignViewerCannotDeliverIncompleteProfileAvatar() throws Exception {
        String owner = uniqueSubject("delivery-private-owner");
        String viewer = uniqueSubject("delivery-private-viewer");
        provisionIncompleteProfile(owner);
        provisionIncompleteProfile(viewer);
        UUID accountId = accountIdFor(owner);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        mediaFixtures.activateAvatar(accountId, mediaId);
        storage.reset();

        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));

        assertThat(storage.totalCalls()).isZero();
        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(mediaId);
        assertThat(mediaFixtures.mediaRow(mediaId)).containsEntry("status", "ACTIVE");
    }

    @ParameterizedTest(name = "does not deliver {0} media")
    @MethodSource("undeliverableStates")
    void pendingReadyUnassociatedReplacedRemovedAndMissingMediaAreNondisclosingNotFound(
            DeliveryState state
    ) throws Exception {
        String owner = uniqueSubject("delivery-state-" + state);
        provisionIncompleteProfile(owner);
        UUID accountId = accountIdFor(owner);
        UUID mediaId = insertDeliveryState(accountId, state);
        storage.reset();

        mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("media.not_found"));

        assertThat(storage.totalCalls()).isZero();
    }

    @Test
    void storageFailureReturnsSanitizedUnavailableWithoutChangingAssociationOrLifecycle() throws Exception {
        String owner = uniqueSubject("delivery-failure-owner");
        provisionIncompleteProfile(owner);
        UUID accountId = accountIdFor(owner);
        UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
        mediaFixtures.activateAvatar(accountId, mediaId);
        Map<String, Object> before = mediaFixtures.mediaRow(mediaId);
        storage.reset();
        storage.failReadRequests();

        String response = mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(owner))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("media.storage_unavailable"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(
                "provider credential",
                "private object",
                (String) before.get("object_key")
        );
        assertThat(mediaFixtures.currentAvatarId(accountId)).isEqualTo(mediaId);
        assertThat(mediaFixtures.mediaRow(mediaId)).isEqualTo(before);
        assertThat(storage.readCalls()).hasSize(1);
        assertThat(storage.readCalls().getFirst().transactionActive()).isFalse();
    }

    private void assertSuccessfulDelivery(String viewer, UUID mediaId) throws Exception {
        Instant before = Instant.now();
        MvcResult result = mockMvc.perform(post("/api/v1/media/{mediaId}/delivery", mediaId)
                        .with(jwt().jwt(token -> token.subject(viewer))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.url").value("https://downloads.example.test/object?signature=test"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        Instant after = Instant.now();

        assertThat(storage.readCalls()).hasSize(1);
        RecordingMediaTestStorage.ReadCall call = storage.readCalls().getFirst();
        Map<String, Object> media = mediaFixtures.mediaRow(mediaId);
        Instant responseExpiry = Instant.parse(JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.expiresAt"
        ));
        assertThat(call.objectKey()).isEqualTo(media.get("object_key"));
        assertThat(call.expiresAt()).isEqualTo(responseExpiry);
        assertThat(call.transactionActive()).isFalse();
        assertThat(call.expiresAt())
                .isAfterOrEqualTo(before.plus(READ_CAPABILITY_TTL).minusMillis(1))
                .isBeforeOrEqualTo(after.plus(READ_CAPABILITY_TTL).plusMillis(1));
        assertThat(storage.totalCalls()).isEqualTo(1);
    }

    private UUID insertDeliveryState(UUID accountId, DeliveryState state) {
        return switch (state) {
            case AWAITING_UPLOAD -> mediaFixtures.insertAwaitingAvatar(accountId);
            case READY -> mediaFixtures.insertReadyAvatar(accountId);
            case ACTIVE_UNASSOCIATED -> {
                UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
                jdbcTemplate.update("UPDATE managed_media SET status = 'ACTIVE' WHERE id = ?", mediaId);
                yield mediaId;
            }
            case REPLACED -> {
                UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
                mediaFixtures.activateAvatar(accountId, mediaId);
                mediaFixtures.detachAvatar(accountId, mediaId, "SUPERSEDED");
                yield mediaId;
            }
            case REMOVED -> {
                UUID mediaId = mediaFixtures.insertReadyAvatar(accountId);
                mediaFixtures.activateAvatar(accountId, mediaId);
                mediaFixtures.detachAvatar(accountId, mediaId, "USER_REMOVED");
                yield mediaId;
            }
            case MISSING -> UUID.randomUUID();
        };
    }

    private void provisionIncompleteProfile(String subject) throws Exception {
        mockMvc.perform(get("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject))))
                .andExpect(status().isOk());
    }

    private void completeProfile(String subject) throws Exception {
        mockMvc.perform(put("/api/v1/profile/me")
                        .with(jwt().jwt(token -> token.subject(subject)))
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "Delivery Owner"
                                }
                                """.formatted(username(subject))))
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

    private static String username(String subject) {
        return "u" + subject.replace("-", "").substring(0, 24);
    }

    private static Stream<DeliveryState> undeliverableStates() {
        return Stream.of(DeliveryState.values());
    }

    private enum DeliveryState {
        AWAITING_UPLOAD,
        READY,
        ACTIVE_UNASSOCIATED,
        REPLACED,
        REMOVED,
        MISSING
    }
}
