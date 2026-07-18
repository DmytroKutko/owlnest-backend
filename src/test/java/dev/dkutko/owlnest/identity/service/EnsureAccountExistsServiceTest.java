package dev.dkutko.owlnest.identity.service;

import dev.dkutko.owlnest.identity.domain.Account;
import dev.dkutko.owlnest.identity.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EnsureAccountExistsServiceTest {

    private static final String PROVIDER = "KEYCLOAK";
    private static final String SUBJECT = "identity-service-subject";

    @Test
    void returnsEstablishedAccountWithoutTakingProvisioningLock() {
        AccountRepository repository = mock(AccountRepository.class);
        Account established = Account.create(
                PROVIDER,
                SUBJECT,
                "old@example.com",
                false,
                Instant.parse("2026-07-18T08:00:00Z")
        );
        AuthenticatedIdentity identity = identity("new@example.com", true);
        when(repository.findByProviderAndExternalSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.of(established));
        EnsureAccountExistsService service = new EnsureAccountExistsService(repository);

        Account result = service.ensureExists(identity);

        assertThat(result).isSameAs(established);
        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.isEmailVerified()).isTrue();
        verify(repository).findByProviderAndExternalSubject(PROVIDER, SUBJECT);
        verify(repository, never()).lockByProviderAndExternalSubject(any(), any());
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void locksAndRechecksMissingAccountBeforeSaving() {
        AccountRepository repository = mock(AccountRepository.class);
        AuthenticatedIdentity identity = identity("created@example.com", false);
        when(repository.findByProviderAndExternalSubject(PROVIDER, SUBJECT))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(repository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Account.class));
        EnsureAccountExistsService service = new EnsureAccountExistsService(repository);

        Account result = service.ensureExists(identity);

        assertThat(result.getProvider()).isEqualTo(PROVIDER);
        assertThat(result.getExternalSubject()).isEqualTo(SUBJECT);
        assertThat(result.getEmail()).isEqualTo("created@example.com");
        InOrder order = inOrder(repository);
        order.verify(repository).findByProviderAndExternalSubject(PROVIDER, SUBJECT);
        order.verify(repository).lockByProviderAndExternalSubject(PROVIDER, SUBJECT);
        order.verify(repository).findByProviderAndExternalSubject(PROVIDER, SUBJECT);
        order.verify(repository).save(any(Account.class));
        verifyNoMoreInteractions(repository);
    }

    private AuthenticatedIdentity identity(String email, boolean emailVerified) {
        return new AuthenticatedIdentity(
                PROVIDER,
                SUBJECT,
                email,
                emailVerified,
                "identity.user",
                "Identity",
                "User"
        );
    }
}
