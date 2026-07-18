package dev.dkutko.owlnest.presence.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.service.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.service.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.service.EnsureAccountExistsService;
import dev.dkutko.owlnest.presence.repository.PresenceRepository;
import dev.dkutko.owlnest.presence.repository.PresenceRepositoryUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PresenceService {

    private static final Duration ONLINE_TIME_TO_LIVE = Duration.ofSeconds(90);

    private final CurrentIdentityProvider currentIdentityProvider;
    private final EnsureAccountExistsService ensureAccountExistsService;
    private final PresenceRepository presenceRepository;

    public PresenceService(
            CurrentIdentityProvider currentIdentityProvider,
            EnsureAccountExistsService ensureAccountExistsService,
            PresenceRepository presenceRepository
    ) {
        this.currentIdentityProvider = currentIdentityProvider;
        this.ensureAccountExistsService = ensureAccountExistsService;
        this.presenceRepository = presenceRepository;
    }

    public void markCurrentAccountOnline() {
        AuthenticatedIdentity identity = currentIdentityProvider.getCurrentIdentity();
        Account account = ensureAccountExistsService.ensureExists(identity);
        presenceRepository.markOnline(account.getId(), Instant.now(), ONLINE_TIME_TO_LIVE);
    }

    public PresenceStatus getStatus(UUID accountId) {
        try {
            return presenceRepository.isOnline(accountId) ? PresenceStatus.ONLINE : PresenceStatus.OFFLINE;
        } catch (PresenceRepositoryUnavailableException exception) {
            return PresenceStatus.UNKNOWN;
        }
    }

}
