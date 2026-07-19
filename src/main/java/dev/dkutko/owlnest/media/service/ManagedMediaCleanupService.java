package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ManagedMediaCleanupService {

    private static final int BATCH_SIZE = 25;

    private final ManagedMediaCleanupTransactionService transactionService;
    private final MediaObjectStorage storage;

    public ManagedMediaCleanupService(
            ManagedMediaCleanupTransactionService transactionService,
            MediaObjectStorage storage
    ) {
        this.transactionService = transactionService;
        this.storage = storage;
    }

    public CleanupResult runBatch() {
        int deleted = 0;
        int retrying = 0;
        for (ManagedMediaCleanupTransactionService.CleanupClaim claim
                : transactionService.expireAndClaim(Instant.now(), BATCH_SIZE)) {
            try {
                storage.delete(claim.objectKey());
                transactionService.complete(claim, Instant.now());
                deleted++;
            } catch (MediaStorageUnavailableException exception) {
                transactionService.retry(claim, Instant.now());
                retrying++;
            }
        }
        return new CleanupResult(deleted, retrying);
    }

    public record CleanupResult(int deleted, int retrying) {
    }
}
