package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

class SecretValueField extends HorizontalLayout {

    private final TextField plain = new TextField();
    private final PasswordField masked = new PasswordField();
    private final Button toggle = new Button(new Icon(VaadinIcon.UNLOCK));

    private boolean secret;

    SecretValueField() {
        setSpacing(false);
        setPadding(false);
        setWidthFull();

        plain.setPlaceholder(Translations.of("entry.field.value"));
        plain.setWidthFull();
        masked.setPlaceholder(Translations.of("entry.field.value"));
        masked.setWidthFull();
        masked.setRevealButtonVisible(true);
        masked.setVisible(false);

        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        toggle.setTooltipText(Translations.of("entry.field.alwaysHide"));
        toggle.addClickListener(event -> setSecret(!secret));

        add(plain, masked, toggle);
        expand(plain);
    }

    void setValue(String value, boolean isSecret) {
        plain.setValue(value == null ? "" : value);
        masked.setValue(value == null ? "" : value);
        setSecret(isSecret);
    }

    String getValue() {
        return secret ? masked.getValue() : plain.getValue();
    }

    boolean isSecret() {
        return secret;
    }

    private void setSecret(boolean isSecret) {
        String current = getValue();
        secret = isSecret;
        plain.setVisible(!isSecret);
        masked.setVisible(isSecret);
        plain.setValue(current);
        masked.setValue(current);
        toggle.setIcon(new Icon(isSecret ? VaadinIcon.LOCK : VaadinIcon.UNLOCK));
        toggle.setTooltipText(Translations.of(isSecret ? "entry.field.alwaysShow" : "entry.field.alwaysHide"));
        expand(isSecret ? masked : plain);
    }
}
