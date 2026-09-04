package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.NewField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class EntryDialog extends Dialog {

    private final TextField title = new TextField(Translations.of("entry.title"));
    private final VerticalLayout rows = new VerticalLayout();
    private final List<EntryFieldRow> fieldRows = new ArrayList<>();

    private final Consumer<EntryDraft> onSave;

    EntryDialog(DecryptedEntry existing, Consumer<EntryDraft> onSave, Runnable onDelete) {
        this.onSave = onSave;

        setHeaderTitle(existing == null
                ? Translations.of("entry.dialog.new")
                : Translations.of("entry.dialog.edit"));
        setWidth("520px");

        title.setWidthFull();
        rows.setPadding(false);
        rows.setSpacing(false);

        Button addField = new Button(Translations.of("entry.addField"), event -> addRow(null));
        addField.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        VerticalLayout content = new VerticalLayout(title, rows, addField);
        content.setPadding(false);
        add(content);

        if (existing == null) {
            addRow(null);
        } else {
            title.setValue(existing.title());
            existing.fields().forEach(this::addRow);
        }

        Button save = new Button(Translations.of("common.save"), event -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(Translations.of("common.cancel"), event -> close());

        getFooter().add(cancel, save);
        if (existing != null) {
            Button delete = new Button(Translations.of("common.delete"), event -> Confirmations.ask(
                    "confirm.deleteEntry.header",
                    Translations.of("confirm.deleteEntry.text", existing.title()),
                    "common.delete",
                    true,
                    () -> {
                        onDelete.run();
                        close();
                    }));
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            getFooter().add(delete);
        }
    }

    private void addRow(DecryptedField field) {
        EntryFieldRow row = new EntryFieldRow(field, removed -> {
            fieldRows.remove(removed);
            rows.remove(removed);
        });
        fieldRows.add(row);
        rows.add(row);
    }

    private void save() {
        title.setInvalid(false);
        List<NewField> fields = fieldRows.stream().map(EntryFieldRow::toField).toList();

        String error = EntryFormValidator.validate(title.getValue(), fields);
        if (error != null) {
            title.setErrorMessage(Translations.of(error));
            title.setInvalid(true);
            return;
        }
        onSave.accept(new EntryDraft(title.getValue().trim(), EntryFormValidator.usable(fields)));
        close();
    }

    private static class EntryFieldRow extends HorizontalLayout {

        private final TextField label = new TextField();
        private final SecretValueField value = new SecretValueField();

        EntryFieldRow(DecryptedField field, Consumer<EntryFieldRow> onRemove) {
            setWidthFull();
            setSpacing(true);
            setPadding(false);

            label.setPlaceholder(Translations.of("entry.field.label"));
            label.setWidth("40%");
            value.setWidthFull();

            if (field != null) {
                label.setValue(field.label());
                value.setValue(field.value(), field.secret());
            }

            Button remove = new Button(Translations.of("common.remove"), event -> onRemove.accept(this));
            remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            add(label, value, remove);
            expand(value);
        }

        NewField toField() {
            return new NewField(label.getValue(), value.getValue(), value.isSecret());
        }
    }

    record EntryDraft(String title, List<NewField> fields) {
    }
}
