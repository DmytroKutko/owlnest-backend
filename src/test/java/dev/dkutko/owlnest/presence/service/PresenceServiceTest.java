package dev.dkutko.owlnest.presence.service;

import dev.dkutko.owlnest.identity.service.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.service.EnsureAccountExistsService;
import dev.dkutko.owlnest.presence.repository.PresenceRepository;
import dev.dkutko.owlnest.presence.repository.PresenceRepositoryUnavailableException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PresenceServiceTest {

    @Test
    void returnsUnknownWhenPresenceRepositoryIsUnavailable() {
        PresenceRepository unavailableRepository = new PresenceRepository() {
            @Override
            public void markOnline(UUID accountId, Instant lastActivityAt, Duration timeToLive) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isOnline(UUID accountId) {
                throw new PresenceRepositoryUnavailableException(new IllegalStateException("Redis unavailable"));
            }
        };
        PresenceService service = new PresenceService(
                mock(CurrentIdentityProvider.class),
                mock(EnsureAccountExistsService.class),
                unavailableRepository
        );

        PresenceStatus status = service.getStatus(UUID.randomUUID());

        assertThat(status).isEqualTo(PresenceStatus.UNKNOWN);
    }

}
