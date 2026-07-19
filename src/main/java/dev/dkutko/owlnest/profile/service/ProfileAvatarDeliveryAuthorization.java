package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.service.ManagedMediaDeliveryAuthorization;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ProfileAvatarDeliveryAuthorization implements ManagedMediaDeliveryAuthorization {

    private final ProfileRepository profileRepository;

    public ProfileAvatarDeliveryAuthorization(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public boolean canDeliver(
            ManagedMediaPurpose purpose,
            UUID mediaId,
            UUID ownerAccountId,
            UUID viewerAccountId
    ) {
        if (purpose != ManagedMediaPurpose.AVATAR) {
            return false;
        }
        return profileRepository.findByAccountId(ownerAccountId)
                .filter(profile -> mediaId.equals(profile.getAvatarMediaId()))
                .filter(profile -> isVisibleTo(profile, viewerAccountId))
                .isPresent();
    }

    private static boolean isVisibleTo(Profile profile, UUID viewerAccountId) {
        return profile.getAccountId().equals(viewerAccountId) || profile.isOnboardingCompleted();
    }
}
