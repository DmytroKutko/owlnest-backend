package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.config.R2Properties;
import dev.dkutko.owlnest.media.domain.ManagedMediaPurpose;
import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CreateMediaUploadService {

    private final CurrentMediaAccountService currentAccountService;
    private final ManagedMediaTransactionService transactionService;
    private final MediaObjectStorage objectStorage;
    private final R2Properties r2Properties;

    public CreateMediaUploadService(
            CurrentMediaAccountService currentAccountService,
            ManagedMediaTransactionService transactionService,
            MediaObjectStorage objectStorage,
            R2Properties r2Properties
    ) {
        this.currentAccountService = currentAccountService;
        this.transactionService = transactionService;
        this.objectStorage = objectStorage;
        this.r2Properties = r2Properties;
    }

    public CreatedMediaUpload create(CreateMediaUploadCommand command) {
        if (command.purpose() != ManagedMediaPurpose.AVATAR
                && command.purpose() != ManagedMediaPurpose.POST_IMAGE) {
            throw new IllegalArgumentException("Only avatar and post image uploads are currently supported");
        }
        objectStorage.ensureAvailable();
        UUID ownerAccountId = currentAccountService.getCurrentAccountId();
        ManagedMediaTransactionService.Reservation reservation = transactionService.reserve(
                ownerAccountId,
                command,
                r2Properties.getUploadUrlTtl()
        );
        MediaObjectStorage.PresignedUpload presignedUpload = objectStorage.createUploadUrl(
                new MediaObjectStorage.UploadUrlRequest(
                        reservation.objectKey(),
                        reservation.contentType(),
                        reservation.sizeBytes(),
                        reservation.uploadExpiresAt(),
                        true
                )
        );
        return new CreatedMediaUpload(
                reservation.mediaId(),
                reservation.purpose(),
                reservation.contentType(),
                reservation.sizeBytes(),
                presignedUpload.url(),
                presignedUpload.requiredHeaders(),
                presignedUpload.expiresAt()
        );
    }
}
