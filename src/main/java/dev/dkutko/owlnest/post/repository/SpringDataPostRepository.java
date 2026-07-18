package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface SpringDataPostRepository extends JpaRepository<Post, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT post FROM Post post WHERE post.id = :postId AND post.deletedAt IS NULL")
    Optional<Post> findActiveByIdForUpdate(@Param("postId") UUID postId);
}
