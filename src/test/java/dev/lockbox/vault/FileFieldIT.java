package dev.lockbox.vault;

import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.storage.StorageException;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
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
class FileFieldIT {

    private static final String PASSWORD = "correct horse battery";
    private static final String CONTENT = "screenshot bytes with the recovery code 4815162342";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-29T01-40-52Z")
            .withUserName("lockbox")
            .withPassword("lockbox-secret");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", MINIO::getS3URL);
        registry.add("storage.access-key", MINIO::getUserName);
        registry.add("storage.secret-key", MINIO::getPassword);
        registry.add("storage.bucket", () -> "lockbox-test");
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private KeyDerivation keyDerivation;

    @Autowired
    private VaultService vaultService;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("The object stored in the bucket is unreadable")
    void storesCiphertextInTheBucket() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile());

        String storageKey = jdbcTemplate.queryForObject(
                "select storage_key from entry_fields where entry_id = ? and kind = 'FILE'",
                String.class, entry.getId());

        byte[] raw = storage.get(storageKey);
        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("4815162342");
        assertThat(raw).hasSize(CONTENT.length() + 28);
    }

    @Test
    @DisplayName("A file field keeps its name in the database and its bytes only in the bucket")
    void keepsMetadataInTheDatabase() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile());

        var row = jdbcTemplate.queryForMap(
                "select label, kind, file_name, size_bytes, value, secret from entry_fields "
                        + "where entry_id = ? and kind = 'FILE'", entry.getId());

        assertThat(row).containsEntry("label", "Screenshot").containsEntry("kind", "FILE")
                .containsEntry("file_name", "recovery.png").containsEntry("secret", true);
        assertThat(row.get("size_bytes")).isEqualTo((long) CONTENT.length());
        assertThat(row.get("value")).isNull();
    }

    @Test
    @DisplayName("The owner downloads the original file back")
    void downloadsOriginalFile() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());
        Long fieldId = fileFieldId(entry);

        StoredFile downloaded = vaultService.openFile(user, masterKey, entry.getId(), fieldId);

        assertThat(downloaded.fileName()).isEqualTo("recovery.png");
        assertThat(downloaded.contentType()).isEqualTo("image/png");
        assertThat(new String(downloaded.content(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("Text and file fields come back in the order they were saved")
    void keepsFieldOrder() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());

        DecryptedEntry opened = vaultService.open(user, masterKey, entry.getId());

        assertThat(opened.fields()).extracting(DecryptedField::label)
                .containsExactly("Service", "Screenshot");
        assertThat(opened.fields()).extracting(DecryptedField::kind)
                .containsExactly(FieldKind.TEXT, FieldKind.FILE);
        assertThat(opened.fields().get(1).file().fileName()).isEqualTo("recovery.png");
        assertThat(opened.fields().get(1).value()).isNull();
    }

    @Test
    @DisplayName("Renaming a file field does not re-upload or lose the file")
    void keepsFileWhenEntryIsSavedAgain() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());
        Long fieldId = fileFieldId(entry);
        String storageKey = storageKeyOf(entry);

        vaultService.update(user, masterKey, entry.getId(), "Recovery codes", List.of(
                NewField.text("Service", "GitHub", false),
                NewField.keptFile(fieldId, "Backup screenshot", true)));

        assertThat(storageKeyOf(entry)).isEqualTo(storageKey);
        StoredFile downloaded = vaultService.openFile(user, masterKey, entry.getId(), fieldId);
        assertThat(new String(downloaded.content(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("Removing the file row deletes the object from the bucket")
    void deletesObjectWhenRowIsRemoved() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());
        String storageKey = storageKeyOf(entry);

        vaultService.update(user, masterKey, entry.getId(), "Recovery codes", List.of(
                NewField.text("Service", "GitHub", false)));

        assertThatThrownBy(() -> storage.get(storageKey)).isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("Deleting the entry deletes the object as well")
    void deletesObjectWithEntry() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());
        String storageKey = storageKeyOf(entry);

        vaultService.delete(user, entry.getId());

        assertThatThrownBy(() -> storage.get(storageKey)).isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("Another user neither opens the file nor learns that it exists")
    void isolatesUsers() {
        User owner = newUser();
        SecretKey ownerKey = masterKeyOf(owner);
        Entry entry = vaultService.create(owner, ownerKey, "Recovery codes", withFile());
        Long fieldId = fileFieldId(entry);
        User stranger = newUser();

        assertThatThrownBy(() -> vaultService.openFile(stranger, masterKeyOf(stranger), entry.getId(), fieldId))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    @DisplayName("A wrong master password cannot open an own file")
    void rejectsWrongMasterPassword() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile());
        Long fieldId = fileFieldId(entry);
        SecretKey wrongKey = keyDerivation.deriveMasterKey("wrong password".toCharArray(), user.getKeySalt());

        assertThatThrownBy(() -> vaultService.openFile(user, wrongKey, entry.getId(), fieldId))
                .isInstanceOf(DecryptionException.class);
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey masterKeyOf(User user) {
        return keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt());
    }

    private List<NewField> withFile() {
        return List.of(
                NewField.text("Service", "GitHub", false),
                NewField.uploadedFile("Screenshot",
                        new StoredFile("recovery.png", "image/png", CONTENT.getBytes(StandardCharsets.UTF_8)),
                        true));
    }

    private Long fileFieldId(Entry entry) {
        return jdbcTemplate.queryForObject(
                "select id from entry_fields where entry_id = ? and kind = 'FILE'", Long.class, entry.getId());
    }

    private String storageKeyOf(Entry entry) {
        return jdbcTemplate.queryForObject(
                "select storage_key from entry_fields where entry_id = ? and kind = 'FILE'",
                String.class, entry.getId());
    }
}
