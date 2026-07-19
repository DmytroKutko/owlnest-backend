package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class GetProfileSummaryServiceTest {

    @Test
    void returnsEmptyBatchWithoutQueryingRepository() {
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        GetProfileSummaryService service = new GetProfileSummaryService(profileRepository);

        Map<UUID, ProfileSummary> result = service.getByAccountIds(Set.of());

        assertThat(result).isEmpty();
        verifyNoMoreInteractions(profileRepository);
    }

    @Test
    void resolvesEverySafeSummaryWithOneBatchRepositoryCall() {
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        GetProfileSummaryService service = new GetProfileSummaryService(profileRepository);
        UUID firstAccountId = UUID.randomUUID();
        UUID secondAccountId = UUID.randomUUID();
        UUID firstAvatarId = UUID.randomUUID();
        Set<UUID> accountIds = Set.of(firstAccountId, secondAccountId);
        when(profileRepository.findSummariesByAccountIds(accountIds)).thenReturn(List.of(
                new ProfileRepository.ProfileSummaryData(
                        firstAccountId,
                        "first.safe",
                        "First Safe Author",
                        firstAvatarId,
                        true
                ),
                new ProfileRepository.ProfileSummaryData(
                        secondAccountId,
                        "second.safe",
                        "Second Safe Author",
                        UUID.randomUUID(),
                        false
                )
        ));

        Map<UUID, ProfileSummary> result = service.getByAccountIds(accountIds);

        assertThat(result).containsOnlyKeys(firstAccountId, secondAccountId);
        assertThat(result.get(firstAccountId)).isEqualTo(new ProfileSummary(
                firstAccountId,
                "first.safe",
                "First Safe Author",
                null,
                firstAvatarId
        ));
        assertThat(result.get(secondAccountId)).isEqualTo(new ProfileSummary(
                secondAccountId,
                "second.safe",
                "Second Safe Author",
                null,
                null
        ));
        verify(profileRepository).findSummariesByAccountIds(accountIds);
        verify(profileRepository, never()).findSummaryByAccountId(firstAccountId);
        verify(profileRepository, never()).findSummaryByAccountId(secondAccountId);
        verifyNoMoreInteractions(profileRepository);
    }

    @Test
    void failsSafelyWhenBatchOmitsRequestedProfileSummary() {
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        GetProfileSummaryService service = new GetProfileSummaryService(profileRepository);
        UUID presentAccountId = UUID.randomUUID();
        UUID missingAccountId = UUID.randomUUID();
        Set<UUID> accountIds = Set.of(presentAccountId, missingAccountId);
        when(profileRepository.findSummariesByAccountIds(accountIds)).thenReturn(List.of(
                new ProfileRepository.ProfileSummaryData(
                        presentAccountId,
                        "present.safe",
                        "Present Safe Author",
                        null,
                        true
                )
        ));

        assertThatThrownBy(() -> service.getByAccountIds(accountIds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("every requested account");
        verify(profileRepository).findSummariesByAccountIds(accountIds);
        verifyNoMoreInteractions(profileRepository);
    }

    @Test
    void failsSafelyWhenBatchReturnsDuplicateAccountSummary() {
        ProfileRepository profileRepository = mock(ProfileRepository.class);
        GetProfileSummaryService service = new GetProfileSummaryService(profileRepository);
        UUID accountId = UUID.randomUUID();
        Set<UUID> accountIds = Set.of(accountId);
        when(profileRepository.findSummariesByAccountIds(accountIds)).thenReturn(List.of(
                new ProfileRepository.ProfileSummaryData(accountId, "first", "First", null, true),
                new ProfileRepository.ProfileSummaryData(accountId, "second", "Second", null, true)
        ));

        assertThatThrownBy(() -> service.getByAccountIds(accountIds))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate account");
        verify(profileRepository).findSummariesByAccountIds(accountIds);
        verifyNoMoreInteractions(profileRepository);
    }
}
