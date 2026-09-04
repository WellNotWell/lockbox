package dev.lockbox.i18n;

import com.vaadin.flow.i18n.I18NProvider;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class LockboxI18NProvider implements I18NProvider {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale RUSSIAN = Locale.of("ru");

    private static final List<Locale> LOCALES = List.of(ENGLISH, RUSSIAN);

    private final MessageSource messageSource;

    public LockboxI18NProvider(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        return messageSource.getMessage(key, params, key, locale == null ? ENGLISH : locale);
    }

    public static Locale supportedOrDefault(String language) {
        return LOCALES.stream()
                .filter(locale -> locale.getLanguage().equals(language))
                .findFirst()
                .orElse(ENGLISH);
    }
}
