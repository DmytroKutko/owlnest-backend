package dev.dkutko.owlnest.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "identity_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_identity_account_provider_subject",
                columnNames = {"provider", "external_subject"}
        )
)
public class Account {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "external_subject", nullable = false, length = 255)
    private String externalSubject;

    @Column(length = 320)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected Account() {
    }

    private Account(
            UUID id,
            String provider,
            String externalSubject,
            String email,
            boolean emailVerified,
            Instant createdAt,
            Instant lastSeenAt
    ) {
        this.id = id;
        this.provider = provider;
        this.externalSubject = externalSubject;
        this.email = normalizeEmail(email);
        this.emailVerified = emailVerified;
        this.createdAt = createdAt;
        this.lastSeenAt = lastSeenAt;
    }

    public static Account create(
            String provider,
            String externalSubject,
            String email,
            boolean emailVerified,
            Instant now
    ) {
        return new Account(
                UUID.randomUUID(),
                provider,
                externalSubject,
                email,
                emailVerified,
                now,
                now
        );
    }

    public void refreshFrom(String currentEmail, boolean currentEmailVerified, Instant now) {
        if (currentEmail != null) {
            email = normalizeEmail(currentEmail);
        }
        emailVerified = currentEmailVerified;
        lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getExternalSubject() {
        return externalSubject;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

}
