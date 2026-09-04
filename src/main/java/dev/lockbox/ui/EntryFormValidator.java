package dev.lockbox.ui;

import dev.lockbox.vault.NewField;

import java.util.List;

final class EntryFormValidator {

    static final String TITLE_EMPTY = "entry.error.titleEmpty";
    static final String NO_FIELDS = "entry.error.noFields";
    static final String LABEL_EMPTY = "entry.error.labelEmpty";
    static final String FILE_MISSING = "entry.error.fileMissing";

    static final int TITLE = -1;

    private EntryFormValidator() {
    }

    record Problem(String messageKey, int rowIndex) {
    }

    static Problem validate(String title, List<NewField> fields) {
        if (title == null || title.isBlank()) {
            return new Problem(TITLE_EMPTY, TITLE);
        }
        if (usable(fields).isEmpty()) {
            return new Problem(NO_FIELDS, TITLE);
        }
        for (int index = 0; index < fields.size(); index++) {
            NewField field = fields.get(index);
            if (field.isEmpty()) {
                continue;
            }
            if (field.label() == null || field.label().isBlank()) {
                return new Problem(LABEL_EMPTY, index);
            }
            if (field.isFile() && !field.hasContent()) {
                return new Problem(FILE_MISSING, index);
            }
        }
        return null;
    }

    static List<NewField> usable(List<NewField> fields) {
        return fields.stream().filter(field -> !field.isEmpty()).toList();
    }
}
