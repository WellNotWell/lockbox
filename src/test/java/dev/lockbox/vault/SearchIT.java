package dev.lockbox.vault;

import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SearchIT {

    private static final String PASSWORD = "correct horse battery";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void unreachableStorage(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", () -> "http://localhost:1");
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private KeyDerivation keyDerivation;

    @Autowired
    private VaultService vaultService;

    @Test
    @DisplayName("Search matches the entry title, case insensitively")
    void findsByTitle() {
        User user = newUser();
        SecretKey key = keyOf(user);
        vaultService.create(user, key, "Prod database", List.of(NewField.text("Host", "prod", false)));
        vaultService.create(user, key, "Home router", List.of(NewField.text("Host", "192.168.0.1", false)));

        assertThat(vaultService.search(user, "PROD")).extracting(Entry::getTitle).containsExactly("Prod database");
    }

    @Test
    @DisplayName("Search also matches field names, because those are stored readable")
    void findsByFieldLabel() {
        User user = newUser();
        vaultService.create(user, keyOf(user), "Home router",
                List.of(NewField.text("Wi-Fi password", "secret", true)));

        assertThat(vaultService.search(user, "wi-fi")).extracting(Entry::getTitle).containsExactly("Home router");
    }

    @Test
    @DisplayName("Search never reaches into encrypted values")
    void ignoresEncryptedValues() {
        User user = newUser();
        vaultService.create(user, keyOf(user), "Home router",
                List.of(NewField.text("Password", "hunter2unique", true)));

        assertThat(vaultService.search(user, "hunter2unique")).isEmpty();
    }

    @Test
    @DisplayName("Search stays inside the vault of one user")
    void staysWithinOneUser() {
        User owner = newUser();
        User stranger = newUser();
        vaultService.create(owner, keyOf(owner), "Prod database", List.of(NewField.text("Host", "prod", false)));

        assertThat(vaultService.search(stranger, "Prod")).isEmpty();
    }

    @Test
    @DisplayName("An empty query lists everything")
    void emptyQueryListsEverything() {
        User user = newUser();
        SecretKey key = keyOf(user);
        vaultService.create(user, key, "First", List.of(NewField.text("a", "1", false)));
        vaultService.create(user, key, "Second", List.of(NewField.text("b", "2", false)));

        assertThat(vaultService.search(user, "   ")).hasSize(2);
        assertThat(vaultService.search(user, null)).hasSize(2);
    }

    @Test
    @DisplayName("An entry is listed once even when several fields match")
    void reportsEachEntryOnce() {
        User user = newUser();
        vaultService.create(user, keyOf(user), "Server", List.of(
                NewField.text("ssh key", "a", false),
                NewField.text("ssh port", "b", false)));

        assertThat(vaultService.search(user, "ssh")).hasSize(1);
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey keyOf(User user) {
        return keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt());
    }
}
