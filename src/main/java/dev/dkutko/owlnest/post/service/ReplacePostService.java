package dev.dkutko.owlnest.post.service;

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
public class ReplacePostService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    private final PostCardQueryService postCardQueryService;

    public ReplacePostService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostRepository postRepository,
            PostContentRepository postContentRepository,
            PostCardQueryService postCardQueryService
    ) {
        this.currentProfileService = currentProfileService;
        this.postRepository = postRepository;
        this.postContentRepository = postContentRepository;
        this.postCardQueryService = postCardQueryService;
    }

    @Transactional
    public PostCard replace(UUID postId, PostWriteCommand command) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        Post post = postRepository.findActiveByIdForUpdate(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        verifyOwnership(post, actor.accountId());
        post.replace(command.postType(), command.title(), command.description(), Instant.now());
        postRepository.save(post);
        postContentRepository.replace(postId, command.labels(), command.media());
        return postCardQueryService.getById(postId, actor.accountId());
    }

    private void verifyOwnership(Post post, UUID actorAccountId) {
        if (!post.getAuthorId().equals(actorAccountId)) {
            throw new PostAccessDeniedException(post.getId());
        }
    }
}
