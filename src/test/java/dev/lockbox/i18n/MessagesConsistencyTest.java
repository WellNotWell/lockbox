package dev.lockbox.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MessagesConsistencyTest {

    private final Properties english = load("/messages.properties");
    private final Properties russian = load("/messages_ru.properties");

    @Test
    @DisplayName("Every English key has a Russian translation")
    void russianCoversEnglish() {
        assertThat(keys(russian)).containsExactlyInAnyOrderElementsOf(keys(english));
    }

    @Test
    @DisplayName("No translation is left empty")
    void noEmptyTranslations() {
        assertThat(english.values()).noneMatch(value -> String.valueOf(value).isBlank());
        assertThat(russian.values()).noneMatch(value -> String.valueOf(value).isBlank());
    }

    @Test
    @DisplayName("Placeholders match between the two languages")
    void placeholdersMatch() {
        for (String key : keys(english)) {
            assertThat(placeholders(russian.getProperty(key)))
                    .as("placeholders of %s", key)
                    .isEqualTo(placeholders(english.getProperty(key)));
        }
    }

    private Set<String> keys(Properties properties) {
        return properties.stringPropertyNames();
    }

    private long placeholders(String text) {
        return text.chars().filter(character -> character == '{').count();
    }

    private Properties load(String resource) {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
