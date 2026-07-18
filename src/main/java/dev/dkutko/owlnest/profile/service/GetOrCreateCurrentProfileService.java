package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.service.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.service.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.service.EnsureAccountExistsService;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GetOrCreateCurrentProfileService {

    private final CurrentIdentityProvider currentIdentityProvider;
    private final EnsureAccountExistsService ensureAccountExistsService;
    private final ProfileRepository profileRepository;

    public GetOrCreateCurrentProfileService(
            CurrentIdentityProvider currentIdentityProvider,
            EnsureAccountExistsService ensureAccountExistsService,
            ProfileRepository profileRepository
    ) {
        this.currentIdentityProvider = currentIdentityProvider;
        this.ensureAccountExistsService = ensureAccountExistsService;
        this.profileRepository = profileRepository;
    }

    @Transactional
    public CurrentProfile getOrCreate() {
        AuthenticatedIdentity identity = currentIdentityProvider.getCurrentIdentity();
        Account account = ensureAccountExistsService.ensureExists(identity);
        Profile profile = getOrCreateProfile(account, Instant.now());

        return currentProfile(account, profile);
    }

    @Transactional
    public CurrentProfile completeOnboarding(CompleteProfileOnboardingCommand command) {
        AuthenticatedIdentity identity = currentIdentityProvider.getCurrentIdentity();
        Account account = ensureAccountExistsService.ensureExists(identity);
        Instant now = Instant.now();
        Profile profile = getOrCreateProfile(account, now);

        if (profileRepository.existsByUsernameIgnoreCaseAndAccountIdNot(command.username(), account.getId())) {
            throw new UsernameAlreadyInUseException(command.username());
        }

        profile.completeOnboarding(
                command.username(),
                command.displayName(),
                command.bio(),
                command.birthDate(),
                command.gender(),
                now
        );

        return currentProfile(account, profile);
    }

    private Profile getOrCreateProfile(Account account, Instant now) {
        Profile existingProfile = profileRepository.findByAccountId(account.getId()).orElse(null);
        if (existingProfile != null) {
            return existingProfile;
        }

        profileRepository.lockProvisioningByAccountId(account.getId());
        return profileRepository.findByAccountId(account.getId())
                .orElseGet(() -> profileRepository.save(createDefaultProfile(account, now)));
    }

    private CurrentProfile currentProfile(Account account, Profile profile) {
        return new CurrentProfile(
                account.getId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getBirthDate(),
                profile.getGender(),
                profile.isOnboardingCompleted(),
                account.getEmail(),
                account.isEmailVerified()
        );
    }

    private Profile createDefaultProfile(Account account, Instant now) {
        String compactId = account.getId().toString().replace("-", "");
        String username = "user_" + compactId.substring(0, 12);
        return Profile.create(account.getId(), username, "OwlNest user", now);
    }

}
