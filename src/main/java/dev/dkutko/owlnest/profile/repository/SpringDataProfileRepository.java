package dev.dkutko.owlnest.profile.repository;

import dev.dkutko.owlnest.profile.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataProfileRepository extends JpaRepository<Profile, UUID> {

    boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId);

    @Query("""
            SELECT profile.accountId AS accountId,
                   profile.username AS nickname,
                   profile.displayName AS displayName
            FROM Profile profile
            WHERE profile.accountId = :accountId
            """)
    Optional<ProfileSummaryView> findSummaryByAccountId(@Param("accountId") UUID accountId);

    interface ProfileSummaryView {

        UUID getAccountId();

        String getNickname();

        String getDisplayName();
    }

}
