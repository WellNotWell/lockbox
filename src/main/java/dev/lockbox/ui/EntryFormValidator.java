package dev.lockbox.ui;

import dev.lockbox.vault.NewField;

import java.util.List;

final class EntryFormValidator {

    static final String TITLE_EMPTY = "entry.error.titleEmpty";
    static final String NO_FIELDS = "entry.error.noFields";
    static final String LABEL_EMPTY = "entry.error.labelEmpty";
    static final String FILE_MISSING = "entry.error.fileMissing";

    private EntryFormValidator() {
    }

    static String validate(String title, List<NewField> fields) {
        if (title == null || title.isBlank()) {
            return TITLE_EMPTY;
        }
        List<NewField> filled = usable(fields);
        if (filled.isEmpty()) {
            return NO_FIELDS;
        }
        if (filled.stream().anyMatch(field -> field.label() == null || field.label().isBlank())) {
            return LABEL_EMPTY;
        }
        if (filled.stream().anyMatch(field -> field.isFile() && !field.hasContent())) {
            return FILE_MISSING;
        }
        return null;
    }

    static List<NewField> usable(List<NewField> fields) {
        return fields.stream().filter(field -> !field.isEmpty()).toList();
    }
}
