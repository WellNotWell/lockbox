package dev.lockbox.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.security.AuthenticationContext;
import dev.lockbox.security.CurrentUser;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authenticationContext, CurrentUser currentUser) {
        H1 name = new H1(Translations.of("app.name"));
        name.getStyle().set("font-size", "var(--aura-font-size-l)").set("margin", "0");

        Span user = new Span(currentUser.name());
        user.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        Button signOut = new Button(Translations.of("common.signOut"), event -> Confirmations.ask(
                "confirm.signOut.header",
                Translations.of("confirm.signOut.text"),
                "common.signOut",
                false,
                authenticationContext::logout));
        signOut.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout header = new HorizontalLayout(name, user, new LanguageSwitcher(), signOut);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(name);
        header.getStyle().set("padding", "0 var(--vaadin-gap-m)");

        addToNavbar(header);
    }
}
