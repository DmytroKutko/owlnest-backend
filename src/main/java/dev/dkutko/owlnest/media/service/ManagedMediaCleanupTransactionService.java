package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import dev.dkutko.owlnest.media.repository.ManagedMediaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ManagedMediaCleanupTransactionService {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);
    private static final int EXPIRY_BATCH_SIZE = 100;

    private final ManagedMediaRepository repository;

    public ManagedMediaCleanupTransactionService(ManagedMediaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<CleanupClaim> expireAndClaim(Instant now, int limit) {
        repository.findExpiredUploadsForUpdate(now, EXPIRY_BATCH_SIZE).forEach(media -> media.expireUpload(now));
        repository.findExpiredReadyForUpdate(now, EXPIRY_BATCH_SIZE).forEach(media -> media.expireReady(now));

        List<CleanupClaim> claims = new ArrayList<>();
        for (ManagedMedia media : repository.findCleanupCandidatesForUpdate(now, limit)) {
            UUID leaseToken = UUID.randomUUID();
            media.claimCleanup(leaseToken, now, now.plus(LEASE_DURATION));
            claims.add(new CleanupClaim(media.getId(), media.getObjectKey(), leaseToken, media.getCleanupAttemptCount()));
        }
        return List.copyOf(claims);
    }

    @Transactional
    public void complete(CleanupClaim claim, Instant now) {
        ManagedMedia media = repository.findByIdForUpdate(claim.mediaId()).orElseThrow(MediaNotFoundException::new);
        media.completeCleanup(claim.leaseToken(), now);
    }

    @Transactional
    public void retry(CleanupClaim claim, Instant now) {
        ManagedMedia media = repository.findByIdForUpdate(claim.mediaId()).orElseThrow(MediaNotFoundException::new);
        media.retryCleanup(
                claim.leaseToken(),
                now,
                now.plus(backoff(claim.attemptCount())),
                "STORAGE_UNAVAILABLE"
        );
    }

    private static Duration backoff(int attemptCount) {
        long minutes = Math.min(60, 1L << Math.min(6, Math.max(0, attemptCount - 1)));
        return Duration.ofMinutes(minutes);
    }

    public record CleanupClaim(UUID mediaId, String objectKey, UUID leaseToken, int attemptCount) {
    }
}
