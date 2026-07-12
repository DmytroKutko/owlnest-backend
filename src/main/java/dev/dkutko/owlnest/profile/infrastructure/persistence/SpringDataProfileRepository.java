package dev.dkutko.owlnest.profile.infrastructure.persistence;

import dev.dkutko.owlnest.profile.domain.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataProfileRepository extends JpaRepository<Profile, UUID> {

    boolean existsByUsernameIgnoreCaseAndAccountIdNot(String username, UUID accountId);

}
