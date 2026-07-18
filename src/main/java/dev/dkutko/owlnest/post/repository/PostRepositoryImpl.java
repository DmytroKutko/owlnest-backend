package dev.dkutko.owlnest.post.repository;

import dev.dkutko.owlnest.post.domain.Post;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PostRepositoryImpl implements PostRepository {

    private final SpringDataPostRepository repository;

    public PostRepositoryImpl(SpringDataPostRepository repository) {
        this.repository = repository;
    }

    @Override
    public Post save(Post post) {
        return repository.saveAndFlush(post);
    }

    @Override
    public Optional<Post> findActiveByIdForUpdate(UUID postId) {
        repository.flush();
        return repository.findActiveByIdForUpdate(postId);
    }
}
