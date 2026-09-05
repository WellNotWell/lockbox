package dev.lockbox.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.HasValidationProperties;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import dev.lockbox.security.MasterKeyStore;
import dev.lockbox.user.MasterPasswordService;
import dev.lockbox.user.PasswordAlreadyUsedException;
import dev.lockbox.user.User;
import dev.lockbox.user.WrongPasswordException;

import javax.crypto.SecretKey;
import java.util.function.Supplier;

class PasswordChangeDialog extends Dialog {

    private final PasswordField current = new PasswordField(Translations.of("password.current"));
    private final PasswordField fresh = new PasswordField(Translations.of("password.new"));
    private final PasswordField confirmation = new PasswordField(Translations.of("password.confirmation"));

    PasswordChangeDialog(Supplier<User> owner, MasterPasswordService service, MasterKeyStore masterKeyStore) {
        setHeaderTitle(Translations.of("password.dialog"));
        setWidth("420px");

        Paragraph warning = new Paragraph(Translations.of("password.warning"));
        warning.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)").set("margin-top", "0");

        prepare(current);
        prepare(fresh);
        prepare(confirmation);
        fresh.setHelperText(Translations.of("register.password.helper",
                RegistrationFormValidator.MIN_PASSWORD_LENGTH));

        VerticalLayout content = new VerticalLayout(warning, current, fresh, confirmation);
        content.setPadding(false);
        add(content);

        Button cancel = new Button(Translations.of("common.cancel"), event -> close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button save = new Button(Translations.of("common.save"), event -> change(owner, service, masterKeyStore));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickShortcut(Key.ENTER);

        getFooter().add(cancel, save);
    }

    private void change(Supplier<User> owner, MasterPasswordService service, MasterKeyStore masterKeyStore) {
        clearErrors();
        RegistrationFormValidator.ValidationResult result = RegistrationFormValidator.validate(
                "unused", fresh.getValue(), confirmation.getValue());
        if (result.passwordError() != null || result.confirmationError() != null) {
            markInvalid(fresh, result.passwordError());
            markInvalid(confirmation, result.confirmationError());
            return;
        }
        if (current.getValue() == null || current.getValue().isEmpty()) {
            markInvalid(current, "password.error.currentEmpty");
            return;
        }
        try {
            SecretKey newKey = service.change(owner.get(), current.getValue(), fresh.getValue());
            masterKeyStore.unlock(newKey);
            close();
            Notifications.success(Translations.of("password.changed"));
        } catch (WrongPasswordException e) {
            markInvalid(current, "password.error.wrongCurrent");
        } catch (PasswordAlreadyUsedException e) {
            markInvalid(fresh, "password.error.alreadyUsed");
        }
    }

    private void prepare(PasswordField field) {
        field.setWidthFull();
        field.setRequiredIndicatorVisible(true);
        field.setValueChangeMode(ValueChangeMode.EAGER);
        field.addValueChangeListener(event -> field.setInvalid(false));
    }

    private void markInvalid(HasValidationProperties field, String messageKey) {
        if (messageKey == null) {
            return;
        }
        field.setErrorMessage(Translations.of(messageKey, RegistrationFormValidator.MIN_PASSWORD_LENGTH));
        field.setInvalid(true);
    }

    private void clearErrors() {
        current.setInvalid(false);
        fresh.setInvalid(false);
        confirmation.setInvalid(false);
    }
}
