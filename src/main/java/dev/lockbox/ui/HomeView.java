package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import dev.lockbox.security.MasterKeyStore;
import jakarta.annotation.security.PermitAll;

@Route("")
@PermitAll
public class HomeView extends VerticalLayout implements HasDynamicTitle {

    public HomeView(AuthenticationContext authenticationContext, MasterKeyStore masterKeyStore) {
        add(new LanguageSwitcher());
        add(new H1(Translations.of("app.name")));
        add(new Paragraph(Translations.of("home.signedInAs",
                authenticationContext.getPrincipalName().orElse("unknown"))));
        add(new Paragraph(Translations.of(masterKeyStore.isUnlocked()
                ? "home.vaultUnlocked"
                : "home.vaultLocked")));
        add(new Button(Translations.of("home.signOut"), event -> authenticationContext.logout()));
    }

    @Override
    public String getPageTitle() {
        return Translations.of("app.name");
    }
}
