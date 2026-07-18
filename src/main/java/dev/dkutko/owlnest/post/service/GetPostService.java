package dev.dkutko.owlnest.post.service;

import dev.dkutko.owlnest.profile.service.CurrentProfile;
import dev.dkutko.owlnest.profile.service.GetOrCreateCurrentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPostService {

    private final GetOrCreateCurrentProfileService currentProfileService;
    private final PostCardQueryService postCardQueryService;

    public GetPostService(
            GetOrCreateCurrentProfileService currentProfileService,
            PostCardQueryService postCardQueryService
    ) {
        this.currentProfileService = currentProfileService;
        this.postCardQueryService = postCardQueryService;
    }

    @Transactional
    public PostCard get(UUID postId) {
        CurrentProfile actor = currentProfileService.getOrCreate();
        return postCardQueryService.getById(postId, actor.accountId());
    }
}
