package dev.dkutko.owlnest.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "post")
public class Post {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 20_000;

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 32)
    private PostType postType;

    @Column(length = MAX_TITLE_LENGTH)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "comment_count", nullable = false)
    private long commentCount;

    @Column(name = "repost_count", nullable = false)
    private long repostCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Post() {
    }

    private Post(
            UUID id,
            UUID authorId,
            PostType postType,
            String title,
            String description,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        this.id = id;
        this.authorId = authorId;
        this.postType = postType;
        this.title = title;
        this.description = description;
        this.likeCount = 0;
        this.commentCount = 0;
        this.repostCount = 0;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static Post create(
            UUID id,
            UUID authorId,
            PostType postType,
            String title,
            String description,
            Instant now
    ) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        Objects.requireNonNull(postType, "postType must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Post(
                id,
                authorId,
                postType,
                normalizeTitle(title),
                validateDescription(description),
                now,
                now,
                null
        );
    }

    public void replace(String title, String description, Instant now) {
        String replacementTitle = normalizeTitle(title);
        String replacementDescription = validateDescription(description);
        Instant replacementTime = Objects.requireNonNull(now, "now must not be null");
        this.title = replacementTitle;
        this.description = replacementDescription;
        this.updatedAt = replacementTime;
    }

    public void softDelete(Instant now) {
        Instant deletionTime = Objects.requireNonNull(now, "now must not be null");
        this.deletedAt = deletionTime;
        this.updatedAt = deletionTime;
    }

    public void recordCommentCreated() {
        try {
            this.commentCount = Math.incrementExact(this.commentCount);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Post comment counter cannot be incremented", exception);
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        PostTextValidation.requireStorableUnicode(title, "title");
        String normalized = PostTextNormalization.stripBoundaryWhitespaceAndControls(title);
        if (normalized.isEmpty()) {
            return null;
        }
        if (PostTextValidation.codePointLength(normalized) > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must not exceed 200 characters");
        }
        return normalized;
    }

    private static String validateDescription(String description) {
        if (description == null) {
            throw new IllegalArgumentException("description must not be blank");
        }
        PostTextValidation.requireStorableUnicode(description, "description");
        if (PostTextNormalization.containsOnlyWhitespaceOrControls(description)) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (PostTextValidation.codePointLength(description) > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must not exceed 20000 characters");
        }
        return description;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public PostType getPostType() {
        return postType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
