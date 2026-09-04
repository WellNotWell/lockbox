package dev.lockbox.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.VaadinSession;
import dev.lockbox.i18n.LocalePreference;
import dev.lockbox.i18n.LockboxI18NProvider;

import java.util.Locale;

class LanguageSwitcher extends HorizontalLayout {

    LanguageSwitcher() {
        setSpacing(false);
        setPadding(false);
        getStyle()
                .set("position", "fixed")
                .set("top", "16px")
                .set("right", "16px")
                .set("gap", "4px")
                .set("z-index", "10");

        add(languageButton(LockboxI18NProvider.ENGLISH), languageButton(LockboxI18NProvider.RUSSIAN));
    }

    private Button languageButton(Locale locale) {
        Button button = new Button(locale.getLanguage().toUpperCase(Locale.ROOT));
        button.setTooltipText(Translations.of("common.language." + locale.getLanguage()));
        button.setWidth("46px");
        button.addThemeVariants(ButtonVariant.LUMO_SMALL);
        if (locale.equals(currentLocale())) {
            button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        } else {
            button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            button.getStyle().set("color", "var(--lumo-secondary-text-color)");
        }
        button.addClickListener(event -> switchTo(locale));
        return button;
    }

    private Locale currentLocale() {
        UI ui = UI.getCurrent();
        Locale locale = ui == null ? LockboxI18NProvider.ENGLISH : ui.getLocale();
        return LockboxI18NProvider.supportedOrDefault(locale.getLanguage());
    }

    private void switchTo(Locale locale) {
        if (locale.equals(currentLocale())) {
            return;
        }
        UI ui = UI.getCurrent();
        ui.setLocale(locale);
        LocalePreference.applyTo(VaadinSession.getCurrent(), locale);
        LocalePreference.remember(locale);
        ui.getPage().reload();
    }
}
