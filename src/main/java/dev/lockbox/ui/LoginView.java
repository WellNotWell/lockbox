package dev.lockbox.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);
        loginForm.setI18n(formMessages());

        add(new LanguageSwitcher(),
                new H1(Translations.of("app.name")),
                new Paragraph(Translations.of("login.subtitle")),
                loginForm,
                new RouterLink(Translations.of("login.toRegister"), RegisterView.class));
    }

    private LoginI18n formMessages() {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle(Translations.of("login.form.title"));
        form.setUsername(Translations.of("login.form.username"));
        form.setPassword(Translations.of("login.form.password"));
        form.setSubmit(Translations.of("login.form.submit"));
        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle(Translations.of("login.error.title"));
        error.setMessage(Translations.of("login.error.message"));
        return i18n;
    }

    @Override
    public String getPageTitle() {
        return Translations.of("login.pageTitle") + " | " + Translations.of("app.name");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
