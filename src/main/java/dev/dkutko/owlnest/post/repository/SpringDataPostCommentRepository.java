package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataPostCommentRepository extends JpaRepository<PostComment, UUID> {
}
