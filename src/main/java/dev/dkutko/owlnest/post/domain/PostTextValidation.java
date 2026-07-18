package dev.dkutko.owlnest.post.domain;

public final class PostTextValidation {

    private PostTextValidation() {
    }

    public static void requireStorableUnicode(String value, String fieldName) {
        for (int index = 0; index < value.length(); index++) {
            char codeUnit = value.charAt(index);
            if (codeUnit == '\0') {
                throw new IllegalArgumentException(fieldName + " must not contain NUL");
            }
            if (Character.isHighSurrogate(codeUnit)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(fieldName + " must contain valid Unicode");
                }
                index++;
            } else if (Character.isLowSurrogate(codeUnit)) {
                throw new IllegalArgumentException(fieldName + " must contain valid Unicode");
            }
        }
    }

    public static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
