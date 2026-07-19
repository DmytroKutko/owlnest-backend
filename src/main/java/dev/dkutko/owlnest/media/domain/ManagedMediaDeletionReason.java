package dev.dkutko.owlnest.media.domain;

public enum ManagedMediaDeletionReason {
    UPLOAD_EXPIRED,
    READY_EXPIRED,
    SUPERSEDED,
    DETACHED,
    USER_REMOVED,
    USER_CANCELLED
}
