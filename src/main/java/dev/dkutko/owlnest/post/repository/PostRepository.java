package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.Post;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findActiveByIdForUpdate(UUID postId);
}
