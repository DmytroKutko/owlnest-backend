package dev.dkutko.owlnest.identity.repository;

import dev.dkutko.owlnest.identity.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByProviderAndExternalSubject(String provider, String externalSubject);

}
