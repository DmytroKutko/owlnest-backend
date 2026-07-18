package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.post.domain.Post;
import dev.dkutko.owlnest.post.repository.PostRepository;
import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeletePostService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostRepository postRepository;

    public DeletePostService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostRepository postRepository
    ) {
        this.currentProfileService = currentProfileService;
        this.postRepository = postRepository;
    }

    @Transactional
    public void delete(UUID postId) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getAuthorId().equals(actor.accountId())) {
            throw new PostAccessDeniedException(postId);
        }
        post.softDelete(Instant.now());
        postRepository.save(post);
    }
}
