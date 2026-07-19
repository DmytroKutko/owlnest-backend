package dev.dkutko.owlnest.media.repository;

import dev.dkutko.owlnest.media.domain.ManagedMedia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface ManagedMediaRepository {

    void lockOwnerReservationQuota(UUID ownerAccountId);

    OwnerStorageUsage getOwnerStorageUsage(UUID ownerAccountId);

    Optional<ManagedMedia> findById(UUID id);

    Optional<ManagedMedia> findByIdForUpdate(UUID id);

    Optional<ManagedMedia> findOwnedById(UUID id, UUID ownerAccountId);

    Optional<ManagedMedia> findOwnedByIdForUpdate(UUID id, UUID ownerAccountId);

    List<ManagedMedia> findAllByIdsForUpdate(List<UUID> ids);

    List<ManagedMedia> findExpiredUploadsForUpdate(Instant now, int limit);

    List<ManagedMedia> findExpiredReadyForUpdate(Instant now, int limit);

    List<ManagedMedia> findCleanupCandidatesForUpdate(Instant now, int limit);

    ManagedMedia save(ManagedMedia media);

    record OwnerStorageUsage(long objectCount, long declaredBytes) {
    }
}
