package dev.lockbox.vault;

import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class VaultIT {

    private static final String PASSWORD = "correct horse battery";
    private static final String SECRET = "SuperSecret123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private KeyDerivation keyDerivation;

    @Autowired
    private VaultService vaultService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("A password saved through the service is unreadable in the database")
    void storesCiphertextInTheDatabase() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Prod database", fields());

        byte[] stored = jdbcTemplate.queryForObject(
                "select value from entry_fields where entry_id = ? and label = 'Password'",
                byte[].class, entry.getId());

        assertThat(stored).isNotNull();
        assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain(SECRET);
    }

    @Test
    @DisplayName("The title stays readable in the database, and that is a deliberate trade off")
    void keepsTitleReadable() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Prod database", fields());

        String title = jdbcTemplate.queryForObject("select title from entries where id = ?",
                String.class, entry.getId());

        assertThat(title).isEqualTo("Prod database");
    }

    @Test
    @DisplayName("The owner reads the original values back")
    void readsValuesBack() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Prod database", fields());

        DecryptedEntry opened = vaultService.open(user, masterKey, entry.getId());

        assertThat(opened.fields()).extracting(DecryptedField::value).containsExactly("postgres://prod", SECRET);
        assertThat(opened.fields()).extracting(DecryptedField::secret).containsExactly(false, true);
    }

    @Test
    @DisplayName("Another user neither sees the entry nor opens it")
    void isolatesUsers() {
        User owner = newUser();
        Entry entry = vaultService.create(owner, masterKeyOf(owner), "Prod database", fields());
        User stranger = newUser();

        assertThat(vaultService.list(stranger)).isEmpty();
        assertThatThrownBy(() -> vaultService.open(stranger, masterKeyOf(stranger), entry.getId()))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    @DisplayName("A wrong master password cannot open an own entry")
    void rejectsWrongMasterPassword() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Prod database", fields());
        SecretKey wrongKey = keyDerivation.deriveMasterKey("wrong password".toCharArray(), user.getKeySalt());

        assertThatThrownBy(() -> vaultService.open(user, wrongKey, entry.getId()))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Deleting an entry removes its fields as well")
    void deletesFieldsWithEntry() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Prod database", fields());

        vaultService.delete(user, entry.getId());

        Integer left = jdbcTemplate.queryForObject("select count(*) from entry_fields where entry_id = ?",
                Integer.class, entry.getId());
        assertThat(left).isZero();
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey masterKeyOf(User user) {
        return keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt());
    }

    private List<NewField> fields() {
        return List.of(new NewField("URL", "postgres://prod", false), new NewField("Password", SECRET, true));
    }
}
