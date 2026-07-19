package dev.dkutko.owlnest.media.repository;

import dev.dkutko.owlnest.media.domain.ManagedMedia;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

@Repository
public class ManagedMediaRepositoryImpl implements ManagedMediaRepository {

    private static final String LOCK_OWNER_RESERVATION_QUOTA_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
            """;

    private static final String OWNER_STORAGE_USAGE_SQL = """
            SELECT COUNT(*), COALESCE(SUM(declared_size_bytes), 0)
            FROM managed_media
            WHERE owner_account_id = ? AND status <> 'DELETED'
            """;

    private final SpringDataManagedMediaRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public ManagedMediaRepositoryImpl(
            SpringDataManagedMediaRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockOwnerReservationQuota(UUID ownerAccountId) {
        jdbcTemplate.execute(LOCK_OWNER_RESERVATION_QUOTA_SQL, (PreparedStatementCallback<Void>) statement -> {
            statement.setString(1, "managed-media-reservation:" + ownerAccountId);
            statement.execute();
            return null;
        });
    }

    @Override
    public OwnerStorageUsage getOwnerStorageUsage(UUID ownerAccountId) {
        return jdbcTemplate.queryForObject(
                OWNER_STORAGE_USAGE_SQL,
                (resultSet, rowNumber) -> new OwnerStorageUsage(resultSet.getLong(1), resultSet.getLong(2)),
                ownerAccountId
        );
    }

    @Override
    public Optional<ManagedMedia> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<ManagedMedia> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id);
    }

    @Override
    public Optional<ManagedMedia> findOwnedById(UUID id, UUID ownerAccountId) {
        return repository.findByIdAndOwnerAccountId(id, ownerAccountId);
    }

    @Override
    public Optional<ManagedMedia> findOwnedByIdForUpdate(UUID id, UUID ownerAccountId) {
        return repository.findOwnedByIdForUpdate(id, ownerAccountId);
    }

    @Override
    public List<ManagedMedia> findAllByIdsForUpdate(List<UUID> ids) {
        return repository.findAllByIdsForUpdate(ids);
    }

    @Override
    public List<ManagedMedia> findExpiredUploadsForUpdate(Instant now, int limit) {
        return repository.findExpiredUploadsForUpdate(now, limit);
    }

    @Override
    public List<ManagedMedia> findExpiredReadyForUpdate(Instant now, int limit) {
        return repository.findExpiredReadyForUpdate(now, limit);
    }

    @Override
    public List<ManagedMedia> findCleanupCandidatesForUpdate(Instant now, int limit) {
        return repository.findCleanupCandidatesForUpdate(now, limit);
    }

    @Override
    public ManagedMedia save(ManagedMedia media) {
        return repository.save(media);
    }
}
