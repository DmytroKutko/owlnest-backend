package dev.dkutko.owlnest.post.domain;

import java.util.Locale;

public enum PostType {
    PERSONAL,
    COMMUNITY;

    private static final String COMMUNITY_EMAIL_SUFFIX = "@owlnest.com";

    public static PostType fromAuthorEmail(String email) {
        if (email == null) {
            return PERSONAL;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return normalizedEmail.endsWith(COMMUNITY_EMAIL_SUFFIX) ? COMMUNITY : PERSONAL;
    }
}
