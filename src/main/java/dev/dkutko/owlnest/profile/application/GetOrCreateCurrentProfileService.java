package dev.dkutko.owlnest.profile.application;

import dev.dkutko.owlnest.identity.application.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.application.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.application.EnsureAccountExistsService;
import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.domain.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GetOrCreateCurrentProfileService {

    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

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
        Profile profile = profileRepository
                .findByAccountId(account.getId())
                .orElseGet(() -> profileRepository.save(createDefaultProfile(account, identity)));

        return new CurrentProfile(
                account.getId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                account.getEmail(),
                account.isEmailVerified()
        );
    }

    private Profile createDefaultProfile(Account account, AuthenticatedIdentity identity) {
        String compactId = account.getId().toString().replace("-", "");
        String username = "user_" + compactId.substring(0, 12);
        return Profile.create(account.getId(), username, displayName(identity), Instant.now());
    }

    private String displayName(AuthenticatedIdentity identity) {
        String fullName = joinNonBlank(identity.givenName(), identity.familyName());
        if (!fullName.isBlank()) {
            return truncate(fullName);
        }
        if (hasText(identity.preferredUsername()) && !identity.preferredUsername().contains("@")) {
            return truncate(identity.preferredUsername());
        }
        if (hasText(identity.email())) {
            int atIndex = identity.email().indexOf('@');
            String emailName = atIndex > 0 ? identity.email().substring(0, atIndex) : identity.email();
            return truncate(emailName);
        }
        return "OwlNest user";
    }

    private String joinNonBlank(String first, String second) {
        String left = hasText(first) ? first.trim() : "";
        String right = hasText(second) ? second.trim() : "";
        return (left + " " + right).trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value) {
        String trimmed = value.trim();
        return trimmed.length() <= MAX_DISPLAY_NAME_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }

}
