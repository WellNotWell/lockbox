package dev.lockbox.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.HasValidationProperties;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.UserAlreadyExistsException;

@Route("register")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements HasDynamicTitle {

    private static final String FIELD_WIDTH = "320px";

    private final TextField username = new TextField(Translations.of("register.username"));
    private final PasswordField password = new PasswordField(Translations.of("register.password"));
    private final PasswordField confirmation = new PasswordField(Translations.of("register.confirmation"));

    private final RegistrationService registrationService;

    public RegisterView(RegistrationService registrationService) {
        this.registrationService = registrationService;

        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        prepare(username);
        prepare(password);
        prepare(confirmation);
        password.setHelperText(Translations.of("register.password.helper",
                RegistrationFormValidator.MIN_PASSWORD_LENGTH));

        Button submit = new Button(Translations.of("register.submit"), event -> register());
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidth(FIELD_WIDTH);
        submit.addClickShortcut(Key.ENTER);

        add(new LanguageSwitcher(),
                new H1(Translations.of("app.name")),
                new Paragraph(Translations.of("register.warning")),
                username, password, confirmation, submit,
                new RouterLink(Translations.of("register.toLogin"), LoginView.class));
    }

    void register() {
        clearErrors();
        RegistrationFormValidator.ValidationResult result = RegistrationFormValidator.validate(
                username.getValue(), password.getValue(), confirmation.getValue());
        if (!result.valid()) {
            markInvalid(username, result.usernameError());
            markInvalid(password, result.passwordError());
            markInvalid(confirmation, result.confirmationError());
            return;
        }
        try {
            registrationService.register(username.getValue().trim(), password.getValue());
            showSuccess();
            getUI().ifPresent(ui -> ui.navigate(LoginView.class));
        } catch (UserAlreadyExistsException e) {
            markInvalid(username, "register.error.usernameTaken");
        }
    }

    private void prepare(com.vaadin.flow.component.textfield.TextFieldBase<?, String> field) {
        field.setWidth(FIELD_WIDTH);
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
        username.setInvalid(false);
        password.setInvalid(false);
        confirmation.setInvalid(false);
    }

    private void showSuccess() {
        Notification notification = Notification.show(Translations.of("register.success"),
                4000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    @Override
    public String getPageTitle() {
        return Translations.of("register.pageTitle") + " | " + Translations.of("app.name");
    }
}
