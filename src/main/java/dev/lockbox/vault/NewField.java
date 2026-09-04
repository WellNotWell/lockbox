package dev.lockbox.vault;

public record NewField(FieldKind kind, String label, boolean secret, String value, StoredFile file, Long keptId) {

    public static NewField text(String label, String value, boolean secret) {
        return new NewField(FieldKind.TEXT, label, secret, value, null, null);
    }

    public static NewField uploadedFile(String label, StoredFile file, boolean secret) {
        return new NewField(FieldKind.FILE, label, secret, null, file, null);
    }

    public static NewField keptFile(Long keptId, String label, boolean secret) {
        return new NewField(FieldKind.FILE, label, secret, null, null, keptId);
    }

    public boolean isFile() {
        return kind == FieldKind.FILE;
    }

    public boolean isEmpty() {
        return switch (kind) {
            case TEXT -> blank(label) && blank(value);
            case FILE -> blank(label) && file == null && keptId == null;
        };
    }

    public boolean hasContent() {
        return kind == FieldKind.TEXT || file != null || keptId != null;
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }
}
