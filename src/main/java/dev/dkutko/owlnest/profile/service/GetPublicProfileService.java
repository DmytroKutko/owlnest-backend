package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.presence.service.PresenceService;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPublicProfileService {

    private final ProfileRepository profileRepository;
    private final PresenceService presenceService;

    public GetPublicProfileService(ProfileRepository profileRepository, PresenceService presenceService) {
        this.profileRepository = profileRepository;
        this.presenceService = presenceService;
    }

    @Transactional(readOnly = true)
    public PublicProfile getByAccountId(UUID accountId) {
        Profile profile = profileRepository
                .findByAccountId(accountId)
                .filter(Profile::isOnboardingCompleted)
                .orElseThrow(() -> new ProfileNotFoundException(accountId));

        return new PublicProfile(
                profile.getAccountId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarMediaId(),
                presenceService.getStatus(accountId)
        );
    }

}
