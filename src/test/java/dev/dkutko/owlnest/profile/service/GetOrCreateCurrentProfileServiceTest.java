package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.service.AuthenticatedIdentity;
import dev.dkutko.owlnest.identity.service.CurrentIdentityProvider;
import dev.dkutko.owlnest.identity.service.EnsureAccountExistsService;
import dev.dkutko.owlnest.profile.domain.Profile;
import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetOrCreateCurrentProfileServiceTest {

    @Test
    void returnsEstablishedProfileWithoutTakingProvisioningLock() {
        CurrentIdentityProvider identityProvider = mock(CurrentIdentityProvider.class);
        EnsureAccountExistsService accountService = mock(EnsureAccountExistsService.class);
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        AuthenticatedIdentity identity = new AuthenticatedIdentity(
                "KEYCLOAK",
                "established-profile-subject",
                "private@example.com",
                true,
                "private.handle",
                "Private",
                "Owner"
        );
        Account account = Account.create(
                identity.provider(),
                identity.subject(),
                identity.email(),
                identity.emailVerified(),
                Instant.parse("2026-07-18T08:00:00Z")
        );
        Profile establishedProfile = Profile.create(
                account.getId(),
                "established.user",
                "Established User",
                Instant.parse("2026-07-18T08:00:00Z")
        );
        when(identityProvider.getCurrentIdentity()).thenReturn(identity);
        when(accountService.ensureExists(identity)).thenReturn(account);
        when(profileRepository.findByAccountId(account.getId())).thenReturn(Optional.of(establishedProfile));
        GetOrCreateCurrentProfileService service = new GetOrCreateCurrentProfileService(
                identityProvider,
                accountService,
                profileRepository
        );

        CurrentProfile result = service.getOrCreate();

        assertThat(result.accountId()).isEqualTo(account.getId());
        assertThat(result.username()).isEqualTo("established.user");
        assertThat(result.displayName()).isEqualTo("Established User");
        verify(profileRepository).findByAccountId(account.getId());
        verify(profileRepository, never()).lockProvisioningByAccountId(any());
        verify(profileRepository, never()).save(any());
        verifyNoMoreInteractions(profileRepository);
    }
}
