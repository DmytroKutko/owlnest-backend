package dev.dkutko.owlnest.post.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "post_comment")
public class PostComment {

    private static final int MAX_TEXT_CODE_POINTS = 5_000;

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "text_content", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PostComment() {
    }

    private PostComment(UUID id, UUID postId, UUID authorId, String text, Instant createdAt) {
        this.id = id;
        this.postId = postId;
        this.authorId = authorId;
        this.text = text;
        this.createdAt = createdAt;
    }

    public static PostComment create(UUID id, UUID postId, UUID authorId, String text, Instant createdAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(postId, "postId must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        return new PostComment(id, postId, authorId, validateText(text), createdAt);
    }

    private static String validateText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("comment text must not be blank");
        }
        PostTextValidation.requireStorableUnicode(text, "comment text");
        if (PostTextNormalization.containsOnlyWhitespaceOrControls(text)) {
            throw new IllegalArgumentException("comment text must not be blank");
        }
        if (PostTextValidation.codePointLength(text) > MAX_TEXT_CODE_POINTS) {
            throw new IllegalArgumentException("comment text must not exceed 5000 Unicode code points");
        }
        return text;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
