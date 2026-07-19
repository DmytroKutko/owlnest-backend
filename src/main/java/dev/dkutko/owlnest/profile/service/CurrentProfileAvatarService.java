package dev.dkutko.owlnest.profile.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CurrentProfileAvatarService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final ProfileAvatarTransactionService transactionService;

    public CurrentProfileAvatarService(
            GetOrCreateCurrentProfileService currentProfileService,
            ProfileAvatarTransactionService transactionService
    ) {
        this.currentProfileService = currentProfileService;
        this.transactionService = transactionService;
    }

    public CurrentProfile replace(UUID mediaId) {
        CurrentProfile currentProfile = currentProfileService.getOrCreate();
        UUID activeMediaId = transactionService.replace(currentProfile.accountId(), mediaId);
        return currentProfile.withAvatarMediaId(activeMediaId);
    }

    public void remove() {
        CurrentProfile currentProfile = currentProfileService.getOrCreate();
        transactionService.remove(currentProfile.accountId());
    }
}
