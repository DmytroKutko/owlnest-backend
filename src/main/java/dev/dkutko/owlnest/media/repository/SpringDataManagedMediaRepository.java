package dev.dkutko.owlnest.media.repository;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

interface SpringDataManagedMediaRepository extends JpaRepository<ManagedMedia, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT media FROM ManagedMedia media WHERE media.id = :id")
    Optional<ManagedMedia> findByIdForUpdate(@Param("id") UUID id);

    Optional<ManagedMedia> findByIdAndOwnerAccountId(UUID id, UUID ownerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT media FROM ManagedMedia media WHERE media.id = :id AND media.ownerAccountId = :ownerAccountId")
    Optional<ManagedMedia> findOwnedByIdForUpdate(
            @Param("id") UUID id,
            @Param("ownerAccountId") UUID ownerAccountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT media FROM ManagedMedia media WHERE media.id IN :ids ORDER BY media.id")
    List<ManagedMedia> findAllByIdsForUpdate(@Param("ids") List<UUID> ids);

    @Query(value = """
            SELECT * FROM managed_media
            WHERE status = 'AWAITING_UPLOAD' AND upload_expires_at <= :now
            ORDER BY upload_expires_at, id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ManagedMedia> findExpiredUploadsForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM managed_media
            WHERE status = 'READY' AND ready_expires_at <= :now
            ORDER BY ready_expires_at, id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ManagedMedia> findExpiredReadyForUpdate(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM managed_media
            WHERE status = 'DELETION_PENDING'
              AND COALESCE(cleanup_next_attempt_at, cleanup_due_at) <= :now
              AND (cleanup_lease_token IS NULL OR cleanup_lease_expires_at <= :now)
            ORDER BY COALESCE(cleanup_next_attempt_at, cleanup_due_at), id
            LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ManagedMedia> findCleanupCandidatesForUpdate(@Param("now") Instant now, @Param("limit") int limit);
}
