package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.service.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.service.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.service.EnsureAccountExistsService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentMediaAccountService {

    private final CurrentIdentityProvider currentIdentityProvider;
    private final EnsureAccountExistsService ensureAccountExistsService;

    public CurrentMediaAccountService(
            CurrentIdentityProvider currentIdentityProvider,
            EnsureAccountExistsService ensureAccountExistsService
    ) {
        this.currentIdentityProvider = currentIdentityProvider;
        this.ensureAccountExistsService = ensureAccountExistsService;
    }

    public UUID getCurrentAccountId() {
        AuthenticatedIdentity identity = currentIdentityProvider.getCurrentIdentity();
        Account account = ensureAccountExistsService.ensureExists(identity);
        return account.getId();
    }
}
