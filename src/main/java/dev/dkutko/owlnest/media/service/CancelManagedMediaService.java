package dev.dkutko.owlnest.media.service;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CancelManagedMediaService {

    private final CurrentMediaAccountService currentAccountService;
    private final ManagedMediaTransactionService transactionService;

    public CancelManagedMediaService(
            CurrentMediaAccountService currentAccountService,
            ManagedMediaTransactionService transactionService
    ) {
        this.currentAccountService = currentAccountService;
        this.transactionService = transactionService;
    }

    public void cancel(UUID mediaId) {
        UUID ownerAccountId = currentAccountService.getCurrentAccountId();
        transactionService.cancel(mediaId, ownerAccountId);
    }
}
