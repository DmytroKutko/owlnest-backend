package dev.dkutko.owlnest.media.service;

import dev.dkutko.owlnest.media.storage.MediaObjectStorage;
import dev.dkutko.owlnest.media.storage.MediaStorageUnavailableException;
import dev.dkutko.owlnest.media.storage.R2MediaObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagedMediaCleanupServiceTest {

    @Test
    void deletesOutsideTransactionAndFinalizesSuccessfulClaim() {
        ManagedMediaCleanupTransactionService transactions = mock(ManagedMediaCleanupTransactionService.class);
        ManagedMediaCleanupTransactionService.CleanupClaim claim = claim("managed/cleanup/success");
        when(transactions.expireAndClaim(any(Instant.class), eq(25))).thenReturn(List.of(claim));
        AtomicBoolean transactionObserved = new AtomicBoolean(true);
        MediaObjectStorage storage = mock(MediaObjectStorage.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            transactionObserved.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(storage).delete(claim.objectKey());
        ManagedMediaCleanupService service = new ManagedMediaCleanupService(transactions, storage);

        ManagedMediaCleanupService.CleanupResult result = service.runBatch();

        assertThat(transactionObserved.get()).isFalse();
        assertThat(result).isEqualTo(new ManagedMediaCleanupService.CleanupResult(1, 0));
        verify(transactions).complete(eq(claim), any(Instant.class));
        verify(transactions, never()).retry(eq(claim), any(Instant.class));
    }

    @Test
    void schedulesBoundedRetryWhenStorageIsUnavailable() {
        ManagedMediaCleanupTransactionService transactions = mock(ManagedMediaCleanupTransactionService.class);
        ManagedMediaCleanupTransactionService.CleanupClaim claim = claim("managed/cleanup/retry");
        when(transactions.expireAndClaim(any(Instant.class), eq(25))).thenReturn(List.of(claim));
        MediaObjectStorage storage = mock(MediaObjectStorage.class);
        doThrow(new MediaStorageUnavailableException()).when(storage).delete(claim.objectKey());
        ManagedMediaCleanupService service = new ManagedMediaCleanupService(transactions, storage);

        ManagedMediaCleanupService.CleanupResult result = service.runBatch();

        assertThat(result).isEqualTo(new ManagedMediaCleanupService.CleanupResult(0, 1));
        verify(transactions).retry(eq(claim), any(Instant.class));
        verify(transactions, never()).complete(eq(claim), any(Instant.class));
    }

    @Test
    void missingR2BucketSchedulesRetryInsteadOfCompletingDeletion() {
        ManagedMediaCleanupTransactionService transactions = mock(ManagedMediaCleanupTransactionService.class);
        ManagedMediaCleanupTransactionService.CleanupClaim claim = claim("managed/cleanup/missing-bucket");
        when(transactions.expireAndClaim(any(Instant.class), eq(25))).thenReturn(List.of(claim));
        S3Client client = mock(S3Client.class);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(
                S3Exception.builder()
                        .statusCode(404)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
                        .build()
        );
        ManagedMediaCleanupService service = new ManagedMediaCleanupService(
                transactions,
                new R2MediaObjectStorage(client, mock(S3Presigner.class), "missing-bucket")
        );

        ManagedMediaCleanupService.CleanupResult result = service.runBatch();

        assertThat(result).isEqualTo(new ManagedMediaCleanupService.CleanupResult(0, 1));
        verify(transactions).retry(eq(claim), any(Instant.class));
        verify(transactions, never()).complete(eq(claim), any(Instant.class));
    }

    private static ManagedMediaCleanupTransactionService.CleanupClaim claim(String objectKey) {
        return new ManagedMediaCleanupTransactionService.CleanupClaim(
                UUID.randomUUID(),
                objectKey,
                UUID.randomUUID(),
                1
        );
    }
}
