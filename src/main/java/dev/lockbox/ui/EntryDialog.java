package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.NewField;
import dev.lockbox.vault.StoredFile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

class EntryDialog extends Dialog {

    private final TextField title = new TextField(Translations.of("entry.title"));
    private final VerticalLayout rows = new VerticalLayout();
    private final List<FieldRow> fieldRows = new ArrayList<>();

    private final long maxFileSize;
    private final Function<Long, StoredFile> opener;
    private final Consumer<EntryDraft> onSave;

    EntryDialog(DecryptedEntry existing, long maxFileSize, Function<Long, StoredFile> opener,
                Consumer<EntryDraft> onSave, Runnable onDelete) {
        this.maxFileSize = maxFileSize;
        this.opener = opener;
        this.onSave = onSave;

        setHeaderTitle(existing == null
                ? Translations.of("entry.dialog.new")
                : Translations.of("entry.dialog.edit"));
        setWidth("620px");

        title.setWidthFull();
        rows.setPadding(false);
        rows.setSpacing(false);
        rows.getStyle().set("gap", "var(--vaadin-gap-s)");

        Button addField = new Button(Translations.of("entry.addField"), event -> addTextRow(null));
        addField.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button addFile = new Button(Translations.of("entry.addFile"), new Icon(VaadinIcon.PAPERCLIP));
        addFile.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        addFile.addClickListener(event -> addFileRow(null));

        Span limit = new Span(Translations.of("entry.file.limit", Sizes.readable(maxFileSize)));
        limit.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)").set("align-self", "center");

        HorizontalLayout actions = new HorizontalLayout(addField, addFile, limit);
        actions.setSpacing(true);
        actions.setPadding(false);
        actions.setAlignItems(HorizontalLayout.Alignment.CENTER);

        VerticalLayout content = new VerticalLayout(title, rows, actions);
        content.setPadding(false);
        add(content);

        if (existing == null) {
            addTextRow(null);
        } else {
            title.setValue(existing.title());
            existing.fields().forEach(field -> {
                if (field.isFile()) {
                    addFileRow(field);
                } else {
                    addTextRow(field);
                }
            });
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

    private void addTextRow(DecryptedField field) {
        register(new TextFieldRow(field, this::remove));
    }

    private void addFileRow(DecryptedField field) {
        register(new FileFieldRow(field, maxFileSize, opener, this::remove));
    }

    private void register(FieldRow row) {
        fieldRows.add(row);
        rows.add(row.layout());
    }

    private void remove(FieldRow row) {
        fieldRows.remove(row);
        rows.remove(row.layout());
    }

    private void save() {
        title.setInvalid(false);
        List<NewField> fields = fieldRows.stream().map(FieldRow::toField).toList();

        String error = EntryFormValidator.validate(title.getValue(), fields);
        if (error != null) {
            title.setErrorMessage(Translations.of(error));
            title.setInvalid(true);
            return;
        }
        onSave.accept(new EntryDraft(title.getValue().trim(), EntryFormValidator.usable(fields)));
        close();
    }

    record EntryDraft(String title, List<NewField> fields) {
    }
}
