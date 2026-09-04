package dev.lockbox.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LockboxI18NProviderTest {

    private final LockboxI18NProvider provider = new LockboxI18NProvider(messageSource());

    @Test
    @DisplayName("Both languages are offered")
    void providesTwoLanguages() {
        assertThat(provider.getProvidedLocales())
                .containsExactly(LockboxI18NProvider.ENGLISH, LockboxI18NProvider.RUSSIAN);
    }

    @Test
    @DisplayName("The same key gives different texts per language")
    void translatesPerLanguage() {
        assertThat(provider.getTranslation("home.signOut", LockboxI18NProvider.ENGLISH)).isEqualTo("Sign out");
        assertThat(provider.getTranslation("home.signOut", LockboxI18NProvider.RUSSIAN)).isEqualTo("Выйти");
    }

    @Test
    @DisplayName("Parameters are substituted in both languages")
    void substitutesParameters() {
        assertThat(provider.getTranslation("home.signedInAs", LockboxI18NProvider.ENGLISH, "lesya"))
                .isEqualTo("Signed in as lesya");
        assertThat(provider.getTranslation("home.signedInAs", LockboxI18NProvider.RUSSIAN, "lesya"))
                .isEqualTo("Вы вошли как lesya");
    }

    @Test
    @DisplayName("Unknown key shows up as the key itself instead of breaking the page")
    void fallsBackToKey() {
        assertThat(provider.getTranslation("no.such.key", LockboxI18NProvider.ENGLISH)).isEqualTo("no.such.key");
    }

    @Test
    @DisplayName("Unknown language falls back to English")
    void fallsBackToEnglish() {
        assertThat(LockboxI18NProvider.supportedOrDefault("de")).isEqualTo(LockboxI18NProvider.ENGLISH);
        assertThat(LockboxI18NProvider.supportedOrDefault("ru")).isEqualTo(LockboxI18NProvider.RUSSIAN);
    }

    @Test
    @DisplayName("Missing locale is treated as English")
    void treatsMissingLocaleAsEnglish() {
        assertThat(provider.getTranslation("home.signOut", (Locale) null)).isEqualTo("Sign out");
    }

    private ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
