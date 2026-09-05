package dev.lockbox.user;

import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.NewField;
import dev.lockbox.vault.VaultService;
import dev.lockbox.user.PasswordAlreadyUsedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MasterPasswordServiceIT {

    private static final String OLD_PASSWORD = "correct horse battery";
    private static final String NEW_PASSWORD = "another long passphrase";
    private static final String SECRET = "SuperSecret123";

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
    private MasterPasswordService masterPasswordService;

    @Autowired
    private KeyDerivation keyDerivation;

    @Autowired
    private VaultService vaultService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Entries written under the old password open under the new one")
    void keepsEntriesReadable() {
        User user = newUser();
        Entry entry = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Prod database", fields());

        SecretKey newKey = masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        DecryptedEntry opened = vaultService.open(user, newKey, entry.getId());
        assertThat(opened.fields()).extracting(DecryptedField::value)
                .containsExactly("postgres://prod", SECRET);
    }

    @Test
    @DisplayName("The derived key matches what the new password produces on its own")
    void returnsTheKeyTheNewPasswordDerives() {
        User user = newUser();
        Entry entry = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Prod database", fields());

        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        DecryptedEntry opened = vaultService.open(user, keyOf(user, NEW_PASSWORD), entry.getId());
        assertThat(opened.fields()).extracting(DecryptedField::label).containsExactly("URL", "Password");
    }

    @Test
    @DisplayName("The old password stops working")
    void retiresTheOldPassword() {
        User user = newUser();
        Entry entry = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Prod database", fields());
        SecretKey staleKey = keyDerivation.deriveMasterKey(OLD_PASSWORD.toCharArray(), user.getKeySalt());

        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(passwordEncoder.matches(OLD_PASSWORD, user.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> vaultService.open(user, staleKey, entry.getId()))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Only the entry key is rewrapped, the encrypted values stay untouched")
    void rewrapsKeysWithoutReencryptingData() {
        User user = newUser();
        Entry entry = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Prod database", fields());
        byte[] valueBefore = storedValue(entry);
        byte[] keyBefore = storedKey(entry);

        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(storedValue(entry)).isEqualTo(valueBefore);
        assertThat(storedKey(entry)).isNotEqualTo(keyBefore);
    }

    @Test
    @DisplayName("A new salt is drawn, so the same password would derive a different key")
    void drawsANewSalt() {
        User user = newUser();
        byte[] saltBefore = user.getKeySalt().clone();

        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(user.getKeySalt()).isNotEqualTo(saltBefore);
    }

    @Test
    @DisplayName("A wrong current password changes nothing")
    void rejectsWrongCurrentPassword() {
        User user = newUser();
        Entry entry = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Prod database", fields());
        byte[] keyBefore = storedKey(entry);
        String hashBefore = user.getPasswordHash();

        assertThatThrownBy(() -> masterPasswordService.change(user, "not my password", NEW_PASSWORD))
                .isInstanceOf(WrongPasswordException.class);

        assertThat(user.getPasswordHash()).isEqualTo(hashBefore);
        assertThat(storedKey(entry)).isEqualTo(keyBefore);
    }

    @Test
    @DisplayName("A password that was used before is refused")
    void refusesAPreviouslyUsedPassword() {
        User user = newUser();
        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        assertThatThrownBy(() -> masterPasswordService.change(user, NEW_PASSWORD, OLD_PASSWORD))
                .isInstanceOf(PasswordAlreadyUsedException.class);
        assertThatThrownBy(() -> masterPasswordService.change(user, NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(PasswordAlreadyUsedException.class);
    }

    @Test
    @DisplayName("Only a handful of old passwords is remembered")
    void forgetsTheOldestPasswords() {
        User user = newUser();
        String current = OLD_PASSWORD;
        for (int i = 0; i < MasterPasswordService.REMEMBERED_PASSWORDS + 1; i++) {
            String next = "passphrase number " + i;
            masterPasswordService.change(user, current, next);
            current = next;
        }

        assertThat(masterPasswordService.change(user, current, OLD_PASSWORD)).isNotNull();
    }

    @Test
    @DisplayName("Entries of another user are left alone")
    void touchesOnlyOwnEntries() {
        User user = newUser();
        User stranger = newUser();
        Entry mine = vaultService.create(user, keyOf(user, OLD_PASSWORD), "Mine", fields());
        Entry theirs = vaultService.create(stranger, keyOf(stranger, OLD_PASSWORD), "Theirs", fields());
        byte[] strangerKeyBefore = storedKey(theirs);

        masterPasswordService.change(user, OLD_PASSWORD, NEW_PASSWORD);

        assertThat(storedKey(theirs)).isEqualTo(strangerKeyBefore);
        assertThat(storedKey(mine)).isNotNull();
        assertThat(vaultService.open(stranger, keyOf(stranger, OLD_PASSWORD), theirs.getId()).fields())
                .extracting(DecryptedField::value).containsExactly("postgres://prod", SECRET);
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), OLD_PASSWORD);
    }

    private SecretKey keyOf(User user, String password) {
        return keyDerivation.deriveMasterKey(password.toCharArray(), user.getKeySalt());
    }

    private byte[] storedValue(Entry entry) {
        return jdbcTemplate.queryForObject(
                "select value from entry_fields where entry_id = ? and label = 'Password'",
                byte[].class, entry.getId());
    }

    private byte[] storedKey(Entry entry) {
        return jdbcTemplate.queryForObject("select data_key from entries where id = ?",
                byte[].class, entry.getId());
    }

    private List<NewField> fields() {
        return List.of(NewField.text("URL", "postgres://prod", false), NewField.text("Password", SECRET, true));
    }
}
