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
public class CreatePostService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostRepository postRepository;
    private final PostContentRepository postContentRepository;
    private final PostCardQueryService postCardQueryService;

    public CreatePostService(
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
    public PostCard create(PostWriteCommand command) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        Post post = Post.create(
                UUID.randomUUID(),
                actor.accountId(),
                command.postType(),
                command.title(),
                command.description(),
                Instant.now()
        );
        postRepository.save(post);
        postContentRepository.replace(post.getId(), command.labels(), command.media());
        return postCardQueryService.getById(post.getId(), actor.accountId());
    }
}
