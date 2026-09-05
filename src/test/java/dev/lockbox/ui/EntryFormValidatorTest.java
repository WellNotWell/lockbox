package dev.lockbox.ui;

import dev.lockbox.vault.NewField;
import dev.lockbox.vault.StagedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntryFormValidatorTest {

    private static final NewField HOST = NewField.text("Host", "prod-db", false);
    private static final NewField EMPTY = NewField.text("", "", false);

    @Test
    @DisplayName("Entry with a title and a filled field is accepted")
    void acceptsFilledForm() {
        assertThat(EntryFormValidator.validate("Prod database", List.of(HOST))).isNull();
    }

    @Test
    @DisplayName("Missing title is reported")
    void rejectsMissingTitle() {
        assertThat(EntryFormValidator.validate("   ", List.of(HOST)).messageKey())
                .isEqualTo(EntryFormValidator.TITLE_EMPTY);
    }

    @Test
    @DisplayName("Entry without any filled field is reported")
    void rejectsEmptyEntry() {
        assertThat(EntryFormValidator.validate("Prod database", List.of(EMPTY)).messageKey())
                .isEqualTo(EntryFormValidator.NO_FIELDS);
        assertThat(EntryFormValidator.validate("Prod database", List.of()).messageKey())
                .isEqualTo(EntryFormValidator.NO_FIELDS);
    }

    @Test
    @DisplayName("The error points at the row that caused it, not at the title")
    void pointsAtTheOffendingRow() {
        NewField named = NewField.stagedFile("", null, false);
        EntryFormValidator.Problem problem = EntryFormValidator.validate("Documents",
                List.of(HOST, NewField.text("", "secret", true), named));

        assertThat(problem.messageKey()).isEqualTo(EntryFormValidator.LABEL_EMPTY);
        assertThat(problem.rowIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("A file row without a file points at its own row")
    void pointsAtTheFileRow() {
        EntryFormValidator.Problem problem = EntryFormValidator.validate("Documents",
                List.of(HOST, NewField.stagedFile("Passport", null, false)));

        assertThat(problem.messageKey()).isEqualTo(EntryFormValidator.FILE_MISSING);
        assertThat(problem.rowIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("Title problems are reported on the title")
    void pointsAtTheTitle() {
        assertThat(EntryFormValidator.validate("   ", List.of(HOST)).rowIndex())
                .isEqualTo(EntryFormValidator.TITLE);
        assertThat(EntryFormValidator.validate("Prod database", List.of(EMPTY)).rowIndex())
                .isEqualTo(EntryFormValidator.TITLE);
    }

    @Test
    @DisplayName("A value without a label is reported")
    void rejectsValueWithoutLabel() {
        assertThat(EntryFormValidator.validate("Prod database",
                List.of(NewField.text("", "secret", true))).messageKey())
                .isEqualTo(EntryFormValidator.LABEL_EMPTY);
    }

    @Test
    @DisplayName("Untouched empty rows are dropped instead of being saved")
    void dropsUntouchedRows() {
        List<NewField> usable = EntryFormValidator.usable(List.of(HOST, EMPTY, EMPTY));

        assertThat(usable).containsExactly(HOST);
    }

    @Test
    @DisplayName("A file row that nobody filled in is dropped like an empty text row")
    void dropsUntouchedFileRow() {
        NewField untouched = NewField.stagedFile("", null, false);

        assertThat(EntryFormValidator.usable(List.of(HOST, untouched))).containsExactly(HOST);
    }

    @Test
    @DisplayName("A named file row without a chosen file is reported")
    void rejectsNamedFileRowWithoutFile() {
        NewField named = NewField.stagedFile("Passport", null, false);

        assertThat(EntryFormValidator.validate("Documents", List.of(named)).messageKey())
                .isEqualTo(EntryFormValidator.FILE_MISSING);
    }

    @Test
    @DisplayName("A file row with a chosen file passes")
    void acceptsFileRow() {
        NewField file = NewField.stagedFile("Passport",
                new StagedFile("staging/1/abc", "passport.png", "image/png", 3, null), true);

        assertThat(EntryFormValidator.validate("Documents", List.of(file))).isNull();
    }

    @Test
    @DisplayName("A field with a label but no value is kept, an empty note is still a note")
    void keepsLabelledFieldWithoutValue() {
        NewField note = NewField.text("Note", "", false);

        assertThat(EntryFormValidator.usable(List.of(note))).containsExactly(note);
        assertThat(EntryFormValidator.validate("Prod database", List.of(note))).isNull();
    }
}
