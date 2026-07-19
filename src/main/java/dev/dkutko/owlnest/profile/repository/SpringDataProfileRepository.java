package dev.dkutko.owlnest.profile.repository;

import dev.dkutko.owlnest.profile.domain.Profile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

interface SpringDataProfileRepository extends JpaRepository<Profile, UUID> {

    boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT profile FROM Profile profile WHERE profile.accountId = :accountId")
    Optional<Profile> findByAccountIdForUpdate(@Param("accountId") UUID accountId);

    @Query("""
            SELECT profile.accountId AS accountId,
                   profile.username AS nickname,
                   profile.displayName AS displayName,
                   profile.avatarMediaId AS avatarMediaId,
                   profile.onboardingCompleted AS onboardingCompleted
            FROM Profile profile
            WHERE profile.accountId = :accountId
            """)
    Optional<ProfileSummaryView> findSummaryByAccountId(@Param("accountId") UUID accountId);

    @Query("""
            SELECT profile.accountId AS accountId,
                   profile.username AS nickname,
                   profile.displayName AS displayName,
                   profile.avatarMediaId AS avatarMediaId,
                   profile.onboardingCompleted AS onboardingCompleted
            FROM Profile profile
            WHERE profile.accountId IN :accountIds
            """)
    List<ProfileSummaryView> findSummariesByAccountIds(@Param("accountIds") Set<UUID> accountIds);

    interface ProfileSummaryView {

        UUID getAccountId();

        String getNickname();

        String getDisplayName();

        UUID getAvatarMediaId();

        boolean isOnboardingCompleted();
    }

}
