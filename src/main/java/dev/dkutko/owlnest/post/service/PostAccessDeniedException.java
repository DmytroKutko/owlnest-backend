package dev.dkutko.owlnest.post.service;

import java.util.UUID;

public class PostAccessDeniedException extends RuntimeException {

    public PostAccessDeniedException(UUID postId) {
        super("Access to post '%s' was denied".formatted(postId));
    }
}
