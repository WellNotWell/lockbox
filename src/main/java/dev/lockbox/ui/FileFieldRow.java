package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.NewField;
import dev.lockbox.vault.StoredFile;

import java.util.function.Consumer;
import java.util.function.Function;

class FileFieldRow extends HorizontalLayout implements FieldRow {

    private final TextField label = new TextField();
    private final FileValueField value;
    private final Button toggle = new Button(new Icon(VaadinIcon.EYE_SLASH));

    private boolean secret;

    FileFieldRow(DecryptedField field, long maxFileSize, Function<Long, StoredFile> opener,
                 Consumer<FieldRow> onRemove) {
        value = new FileValueField(maxFileSize, opener);

        setWidthFull();
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        label.setPlaceholder(Translations.of("entry.field.label"));
        label.setWidth("34%");
        value.setWidthFull();

        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        toggle.addClickListener(event -> setSecret(!secret));

        Button remove = new Button(Translations.of("common.remove"), event -> onRemove.accept(this));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        add(label, value, toggle, remove);
        expand(value);

        if (field != null) {
            label.setValue(field.label());
            value.setExisting(field);
        }
        setSecret(field != null && field.secret());
    }

    private void setSecret(boolean isSecret) {
        secret = isSecret;
        toggle.setIcon(new Icon(isSecret ? VaadinIcon.EYE : VaadinIcon.EYE_SLASH));
        toggle.setTooltipText(Translations.of(isSecret ? "entry.field.isSecret" : "entry.field.markSecret"));
        value.setSecret(isSecret);
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
        Long keptId = value.keptId();
        return keptId != null
                ? NewField.keptFile(keptId, label.getValue(), secret)
                : NewField.uploadedFile(label.getValue(), value.uploadedFile(), secret);
    }
}
