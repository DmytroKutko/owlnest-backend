package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.media.service.AvatarMediaLifecycleService;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProfileAvatarTransactionService {

    private final ProfileRepository profileRepository;
    private final AvatarMediaLifecycleService mediaLifecycleService;

    public ProfileAvatarTransactionService(
            ProfileRepository profileRepository,
            AvatarMediaLifecycleService mediaLifecycleService
    ) {
        this.profileRepository = profileRepository;
        this.mediaLifecycleService = mediaLifecycleService;
    }

    @Transactional
    public UUID replace(UUID accountId, UUID candidateMediaId) {
        Profile profile = lockProfile(accountId);
        UUID currentMediaId = profile.getAvatarMediaId();
        Instant now = Instant.now();
        mediaLifecycleService.replace(accountId, currentMediaId, candidateMediaId, now);
        profile.setAvatarMediaId(candidateMediaId, now);
        profileRepository.save(profile);
        return candidateMediaId;
    }

    @Transactional
    public void remove(UUID accountId) {
        Profile profile = lockProfile(accountId);
        UUID currentMediaId = profile.getAvatarMediaId();
        if (currentMediaId == null) {
            return;
        }

        Instant now = Instant.now();
        mediaLifecycleService.remove(accountId, currentMediaId, now);
        profile.clearAvatarMediaId(now);
        profileRepository.save(profile);
    }

    private Profile lockProfile(UUID accountId) {
        return profileRepository.findByAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ProfileNotFoundException(accountId));
    }

}
