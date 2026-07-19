package dev.dkutko.owlnest.profile.repository;

import dev.dkutko.owlnest.profile.domain.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class ProfileRepositoryImpl implements ProfileRepository {

    private static final String LOCK_PROFILE_PROVISIONING_SQL = """
            SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
            """;

    private final SpringDataProfileRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public ProfileRepositoryImpl(
            SpringDataProfileRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void lockProvisioningByAccountId(UUID accountId) {
        jdbcTemplate.execute(LOCK_PROFILE_PROVISIONING_SQL, (PreparedStatementCallback<Void>) statement -> {
            statement.setString(1, "profile:" + accountId);
            statement.execute();
            return null;
        });
    }

    @Override
    public Optional<Profile> findByAccountId(UUID accountId) {
        return repository.findById(accountId);
    }

    @Override
    public Optional<Profile> findByAccountIdForUpdate(UUID accountId) {
        return repository.findByAccountIdForUpdate(accountId);
    }

    @Override
    public Optional<ProfileSummaryData> findSummaryByAccountId(UUID accountId) {
        return repository.findSummaryByAccountId(accountId)
                .map(ProfileRepositoryImpl::toSummaryData);
    }

    @Override
    public List<ProfileSummaryData> findSummariesByAccountIds(Set<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return repository.findSummariesByAccountIds(accountIds).stream()
                .map(ProfileRepositoryImpl::toSummaryData)
                .toList();
    }

    @Override
    public boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId) {
        return repository.existsByUsernameIgnoreCaseAndAccountIdNot(username, accountId);
    }

    @Override
    public Profile save(Profile profile) {
        return repository.save(profile);
    }

    private static ProfileSummaryData toSummaryData(SpringDataProfileRepository.ProfileSummaryView summary) {
        return new ProfileSummaryData(
                summary.getAccountId(),
                summary.getNickname(),
                summary.getDisplayName(),
                summary.getAvatarMediaId(),
                summary.isOnboardingCompleted()
        );
    }

}
