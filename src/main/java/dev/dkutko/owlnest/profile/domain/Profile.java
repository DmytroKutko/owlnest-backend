package dev.dkutko.owlnest.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 500)
    private String bio;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Gender gender;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
    }

    private Profile(
            UUID accountId,
            String username,
            String displayName,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.accountId = accountId;
        this.username = username;
        this.displayName = displayName;
        this.onboardingCompleted = false;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Profile create(UUID accountId, String username, String displayName, Instant now) {
        return new Profile(accountId, username, displayName, now, now);
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    public UUID getAvatarMediaId() {
        return avatarMediaId;
    }

    public void setAvatarMediaId(UUID avatarMediaId, Instant now) {
        this.avatarMediaId = Objects.requireNonNull(avatarMediaId, "avatarMediaId must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void clearAvatarMediaId(Instant now) {
        avatarMediaId = null;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void completeOnboarding(
            String username,
            String displayName,
            String bio,
            LocalDate birthDate,
            Gender gender,
            Instant now
    ) {
        this.username = Objects.requireNonNull(username, "username must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.bio = bio;
        this.birthDate = birthDate;
        this.gender = gender;
        this.onboardingCompleted = true;
        this.updatedAt = now;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
