package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.NewField;

import java.util.function.Consumer;

class FileFieldRow extends HorizontalLayout implements FieldRow {

    private final TextField label = new TextField();
    private final FileValueField value;
    private final Button toggle = new Button(new Icon(VaadinIcon.UNLOCK));

    private boolean secret;

    FileFieldRow(DecryptedField field, long maxFileSize, FileAccess access, Consumer<FieldRow> onRemove) {
        value = new FileValueField(maxFileSize, access);

        setWidthFull();
        setSpacing(true);
        setPadding(false);
        setAlignItems(Alignment.CENTER);

        label.setPlaceholder(Translations.of("entry.field.label"));
        label.setWidth("34%");
        label.getStyle().set("flex", "0 0 34%");
        value.setWidthFull();
        value.getStyle().set("min-width", "0");

        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        toggle.getStyle().set("flex", "0 0 auto");
        toggle.addClickListener(event -> setSecret(!secret));

        Button remove = new Button(Translations.of("common.remove"), event -> onRemove.accept(this));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        remove.getStyle().set("flex", "0 0 auto");

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
        toggle.setIcon(new Icon(isSecret ? VaadinIcon.LOCK : VaadinIcon.UNLOCK));
        toggle.setTooltipText(Translations.of(isSecret ? "entry.field.alwaysShow" : "entry.field.alwaysHide"));
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
                : NewField.stagedFile(label.getValue(), value.stagedFile(), secret);
    }
}
