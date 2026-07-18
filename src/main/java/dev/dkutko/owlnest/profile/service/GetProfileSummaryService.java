package dev.dkutko.owlnest.profile.service;

import dev.dkutko.owlnest.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .map(summary -> new ProfileSummary(
                        summary.accountId(),
                        summary.nickname(),
                        summary.displayName(),
                        null
                ))
                .orElseThrow(() -> new ProfileNotFoundException(accountId));
    }
}
