package dev.dkutko.owlnest.post.service;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

final class PostListCursorCodec {

    private static final String PREFIX = "v1.";
    private static final byte PAYLOAD_VERSION = 1;
    private static final int PAYLOAD_SIZE = 29;
    private static final int MAX_CURSOR_LENGTH = 1_024;
    private static final long POSTGRES_MIN_EPOCH_SECOND = -210_866_803_200L;
    private static final long POSTGRES_MAX_EPOCH_SECOND = 9_224_318_015_999L;

    private PostListCursorCodec() {
    }

    static String encode(Instant createdAt, UUID postId) {
        validateTimestamp(createdAt.getEpochSecond(), createdAt.getNano());
        ByteBuffer payload = ByteBuffer.allocate(PAYLOAD_SIZE);
        payload.put(PAYLOAD_VERSION);
        payload.putLong(createdAt.getEpochSecond());
        payload.putInt(createdAt.getNano());
        writeUuid(payload, postId);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.array());
    }

    static Position decode(String cursor) {
        if (cursor == null || cursor.isBlank() || cursor.length() > MAX_CURSOR_LENGTH || !cursor.startsWith(PREFIX)) {
            throw invalidCursor();
        }

        String encoded = cursor.substring(PREFIX.length());
        if (encoded.isEmpty() || encoded.indexOf('=') >= 0) {
            throw invalidCursor();
        }

        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw invalidCursor();
        }
        if (bytes.length != PAYLOAD_SIZE
                || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded)) {
            throw invalidCursor();
        }

        ByteBuffer payload = ByteBuffer.wrap(bytes);
        if (payload.get() != PAYLOAD_VERSION) {
            throw invalidCursor();
        }

        long epochSecond = payload.getLong();
        int nano = payload.getInt();
        validateTimestamp(epochSecond, nano);
        Instant createdAt;
        try {
            createdAt = Instant.ofEpochSecond(epochSecond, nano);
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
        UUID postId = readUuid(payload);
        if (payload.hasRemaining()) {
            throw invalidCursor();
        }
        return new Position(createdAt, postId);
    }

    private static void validateTimestamp(long epochSecond, int nano) {
        if (epochSecond < POSTGRES_MIN_EPOCH_SECOND
                || epochSecond > POSTGRES_MAX_EPOCH_SECOND
                || nano < 0
                || nano > 999_999_999) {
            throw invalidCursor();
        }
    }

    private static void writeUuid(ByteBuffer payload, UUID value) {
        payload.putLong(value.getMostSignificantBits());
        payload.putLong(value.getLeastSignificantBits());
    }

    private static UUID readUuid(ByteBuffer payload) {
        return new UUID(payload.getLong(), payload.getLong());
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("Invalid post list cursor");
    }

    record Position(Instant createdAt, UUID postId) {
    }
}
