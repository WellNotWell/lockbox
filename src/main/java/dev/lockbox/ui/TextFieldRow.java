package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.NewField;

import java.util.function.Consumer;

class TextFieldRow extends HorizontalLayout implements FieldRow {

    private final TextField label = new TextField();
    private final SecretValueField value = new SecretValueField();

    TextFieldRow(DecryptedField field, Consumer<FieldRow> onRemove) {
        setWidthFull();
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        label.setPlaceholder(Translations.of("entry.field.label"));
        label.setWidth("34%");
        label.getStyle().set("flex", "0 0 34%");
        value.setWidthFull();
        value.getStyle().set("min-width", "0");

        if (field != null) {
            label.setValue(field.label());
            value.setValue(field.value(), field.secret());
        }

        Button remove = new Button(Translations.of("common.remove"), event -> onRemove.accept(this));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        remove.getStyle().set("flex", "0 0 auto");

        add(label, value, remove);
        expand(value);
    }

    @Override
    public HorizontalLayout layout() {
        return this;
    }

    @Override
    public void showError(String messageKey) {
        label.setErrorMessage(Translations.of(messageKey));
        label.setInvalid(true);
    }

    @Override
    public void clearError() {
        label.setInvalid(false);
    }

    @Override
    public NewField toField() {
        return NewField.text(label.getValue(), value.getValue(), value.isSecret());
    }
}
