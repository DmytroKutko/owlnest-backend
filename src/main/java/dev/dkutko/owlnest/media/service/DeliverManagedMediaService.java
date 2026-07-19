package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.config.R2Properties;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeliverManagedMediaService {

    private final CurrentMediaAccountService currentAccountService;
    private final ManagedMediaDeliveryTransactionService transactionService;
    private final MediaObjectStorage objectStorage;
    private final R2Properties r2Properties;

    public DeliverManagedMediaService(
            CurrentMediaAccountService currentAccountService,
            ManagedMediaDeliveryTransactionService transactionService,
            MediaObjectStorage objectStorage,
            R2Properties r2Properties
    ) {
        this.currentAccountService = currentAccountService;
        this.transactionService = transactionService;
        this.objectStorage = objectStorage;
        this.r2Properties = r2Properties;
    }

    public MediaDelivery deliver(UUID mediaId) {
        UUID viewerAccountId = currentAccountService.getCurrentAccountId();
        ManagedMediaDeliveryTransactionService.DeliveryTarget target =
                transactionService.authorize(mediaId, viewerAccountId);
        Instant expiresAt = Instant.now().plus(r2Properties.getReadUrlTtl());
        MediaObjectStorage.PresignedRead presignedRead = objectStorage.createReadUrl(
                target.objectKey(),
                expiresAt
        );
        return new MediaDelivery(presignedRead.url(), presignedRead.expiresAt());
    }
}
