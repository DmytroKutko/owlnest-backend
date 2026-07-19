package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.storage.MediaObjectNotFoundException;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ConfirmMediaUploadService {

    private final CurrentMediaAccountService currentAccountService;
    private final ManagedMediaTransactionService transactionService;
    private final MediaObjectStorage objectStorage;

    public ConfirmMediaUploadService(
            CurrentMediaAccountService currentAccountService,
            ManagedMediaTransactionService transactionService,
            MediaObjectStorage objectStorage
    ) {
        this.currentAccountService = currentAccountService;
        this.transactionService = transactionService;
        this.objectStorage = objectStorage;
    }

    public ConfirmedMedia confirm(UUID mediaId) {
        UUID ownerAccountId = currentAccountService.getCurrentAccountId();
        ManagedMediaTransactionService.ConfirmationPreflight preflight =
                transactionService.preflightConfirmation(mediaId, ownerAccountId);
        if (preflight.isEstablished()) {
            return preflight.establishedConfirmation();
        }

        MediaObjectStorage.ObjectMetadata metadata;
        try {
            metadata = objectStorage.inspect(preflight.objectKey());
        } catch (MediaObjectNotFoundException exception) {
            throw new MediaUploadIncompleteException();
        }
        return transactionService.confirm(mediaId, ownerAccountId, metadata);
    }
}
