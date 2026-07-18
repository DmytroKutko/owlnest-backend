package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class GetProfileSummaryService {

    private final ProfileRepository profileRepository;

    public GetProfileSummaryService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public ProfileSummary getByAccountId(UUID accountId) {
        return profileRepository.findSummaryByAccountId(accountId)
                .map(GetProfileSummaryService::toSummary)
                .orElseThrow(() -> new ProfileNotFoundException(accountId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProfileSummary> getByAccountIds(Set<UUID> accountIds) {
        Set<UUID> requestedIds = Set.copyOf(Objects.requireNonNull(accountIds, "accountIds must not be null"));
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, ProfileSummary> summaries = new LinkedHashMap<>();
        for (ProfileRepository.ProfileSummaryData data : profileRepository.findSummariesByAccountIds(requestedIds)) {
            ProfileSummary previous = summaries.put(data.accountId(), toSummary(data));
            if (previous != null) {
                throw new IllegalStateException("Profile summary query returned a duplicate account");
            }
        }
        if (!summaries.keySet().equals(requestedIds)) {
            throw new IllegalStateException("Profile summary query did not return every requested account");
        }
        return Map.copyOf(summaries);
    }

    private static ProfileSummary toSummary(ProfileRepository.ProfileSummaryData summary) {
        return new ProfileSummary(
                summary.accountId(),
                summary.nickname(),
                summary.displayName(),
                null
        );
    }
}
