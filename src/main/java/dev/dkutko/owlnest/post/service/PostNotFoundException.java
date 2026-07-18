package dev.dkutko.owlnest.post.service;

import java.util.UUID;

public class PostNotFoundException extends RuntimeException {

    public PostNotFoundException(UUID postId) {
        super("Post '%s' was not found".formatted(postId));
    }
}
