package dev.dkutko.owlnest.profile.service;

import java.util.UUID;

public record ProfileSummary(
        UUID accountId,
        String nickname,
        String displayName,
        String avatarUrl,
        UUID avatarMediaId
) {
}
