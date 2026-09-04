package dev.lockbox.ui;

final class Sizes {

    private Sizes() {
    }

    static String readable(long bytes) {
        if (bytes < 1024) {
            return Translations.of("size.b", bytes);
        }
        if (bytes < 1024 * 1024) {
            return Translations.of("size.kb", Math.round(bytes / 1024.0));
        }
        return Translations.of("size.mb", bytes / (1024.0 * 1024.0));
    }
}
