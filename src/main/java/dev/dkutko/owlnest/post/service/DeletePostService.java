package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.media.service.PostImageMediaLifecycleService;
import dev.dkutko.owlnest.post.domain.Post;
import dev.dkutko.owlnest.post.repository.PostContentRepository;
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
    private final PostContentRepository postContentRepository;
    private final PostImageMediaLifecycleService mediaLifecycleService;

    public DeletePostService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostRepository postRepository,
            PostContentRepository postContentRepository,
            PostImageMediaLifecycleService mediaLifecycleService
    ) {
        this.currentProfileService = currentProfileService;
        this.postRepository = postRepository;
        this.postContentRepository = postContentRepository;
        this.mediaLifecycleService = mediaLifecycleService;
    }

    @Transactional
    public void delete(UUID postId) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getAuthorId().equals(actor.accountId())) {
            throw new PostAccessDeniedException(postId);
        }
        Instant now = Instant.now();
        mediaLifecycleService.detachAll(
                actor.accountId(),
                postContentRepository.findManagedMediaIds(postId),
                now
        );
        post.softDelete(now);
        postRepository.save(post);
    }
}
