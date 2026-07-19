package dev.dkutko.owlnest.profile.repository;

import dev.dkutko.owlnest.profile.domain.Profile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProfileRepository {

    void lockProvisioningByAccountId(UUID accountId);

    Optional<Profile> findByAccountId(UUID accountId);

    Optional<Profile> findByAccountIdForUpdate(UUID accountId);

    Optional<ProfileSummaryData> findSummaryByAccountId(UUID accountId);

    List<ProfileSummaryData> findSummariesByAccountIds(Set<UUID> accountIds);

    boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId);

    Profile save(Profile profile);

    record ProfileSummaryData(
            UUID accountId,
            String nickname,
            String displayName,
            UUID avatarMediaId,
            boolean onboardingCompleted
    ) {
    }

}
