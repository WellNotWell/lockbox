package dev.lockbox.backup;

import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.user.MasterPasswordService;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.User;
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.FieldFileStore;
import dev.lockbox.vault.NewField;
import dev.lockbox.vault.StagedFile;
import dev.lockbox.vault.VaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BackupServiceIT {

    private static final String PASSWORD = "correct horse battery";
    private static final String NEW_PASSWORD = "another long passphrase";
    private static final String SECRET = "SuperSecret123";
    private static final String FILE_BODY = "recovery code 4815162342";
    private static final String ARCHIVE_PASSWORD = "archive passphrase here";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-29T01-40-52Z")
            .withUserName("lockbox").withPassword("lockbox-secret");

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("storage.endpoint", MINIO::getS3URL);
        registry.add("storage.access-key", MINIO::getUserName);
        registry.add("storage.secret-key", MINIO::getPassword);
        registry.add("storage.bucket", () -> "lockbox-backup");
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
    private FieldFileStore fileStore;

    @Autowired
    private BackupService backupService;

    @Test
    @DisplayName("A restored vault reads back exactly what was exported")
    void restoresEntriesAndFiles() throws IOException {
        User owner = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Prod database", withFile(owner));

        byte[] archive = export(owner);
        User restoredInto = newUser();
        SecretKey restoredKey = keyOf(restoredInto, PASSWORD);

        int count = backupService.restore(restoredInto, restoredKey, ARCHIVE_PASSWORD,
                new ByteArrayInputStream(archive));

        assertThat(count).isEqualTo(1);
        Entry entry = vaultService.list(restoredInto).getFirst();
        DecryptedEntry opened = vaultService.open(restoredInto, restoredKey, entry.getId());
        assertThat(opened.title()).isEqualTo("Prod database");
        assertThat(opened.fields()).extracting(DecryptedField::label).containsExactly("Password", "Screenshot");
        assertThat(opened.fields().getFirst().value()).isEqualTo(SECRET);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        vaultService.writeFile(restoredInto, restoredKey, entry.getId(), opened.fields().get(1).id(), file);
        assertThat(file.toString(StandardCharsets.UTF_8)).isEqualTo(FILE_BODY);
    }

    @Test
    @DisplayName("The archive carries ciphertext, not the secrets themselves")
    void archiveHoldsNoPlainText() throws IOException {
        User owner = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Prod database", withFile(owner));

        byte[] archive = export(owner);

        assertThat(new String(archive, StandardCharsets.ISO_8859_1))
                .doesNotContain(SECRET).doesNotContain(FILE_BODY);
        assertThat(manifestOf(archive)).contains("Prod database").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("The archive password is what opens the archive, not the master password")
    void ownsItsPasswordIndependently() throws IOException {
        User owner = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Prod database", withFile(owner));
        byte[] archive = export(owner);
        User restoredInto = newUser();

        assertThatThrownBy(() -> backupService.restore(restoredInto, keyOf(restoredInto, PASSWORD),
                PASSWORD, new ByteArrayInputStream(archive)))
                .isInstanceOf(DecryptionException.class);

        backupService.restore(restoredInto, keyOf(restoredInto, PASSWORD), ARCHIVE_PASSWORD,
                new ByteArrayInputStream(archive));
        assertThat(vaultService.list(restoredInto)).hasSize(1);
    }

    @Test
    @DisplayName("Changing the master password does not affect an archive taken before it")
    void survivesAPasswordChange() throws IOException {
        User owner = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Prod database", withFile(owner));
        byte[] archive = export(owner);

        masterPasswordService.change(owner, PASSWORD, NEW_PASSWORD);

        User restoredInto = newUser();
        SecretKey restoredKey = keyOf(restoredInto, PASSWORD);
        backupService.restore(restoredInto, restoredKey, ARCHIVE_PASSWORD, new ByteArrayInputStream(archive));

        Entry entry = vaultService.list(restoredInto).getFirst();
        assertThat(vaultService.open(restoredInto, restoredKey, entry.getId()).fields().getFirst().value())
                .isEqualTo(SECRET);
    }

    @Test
    @DisplayName("A wrong archive password is refused instead of restoring garbage")
    void refusesWrongArchivePassword() throws IOException {
        User owner = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Prod database", withFile(owner));
        byte[] archive = export(owner);
        User restoredInto = newUser();

        assertThatThrownBy(() -> backupService.restore(restoredInto, keyOf(restoredInto, PASSWORD),
                "not the archive password", new ByteArrayInputStream(archive)))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Something that is not a Lockbox archive is refused")
    void refusesForeignArchive() {
        User owner = newUser();

        assertThatThrownBy(() -> backupService.restore(owner, keyOf(owner, PASSWORD), ARCHIVE_PASSWORD,
                new ByteArrayInputStream("just some text".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BackupFormatException.class);
    }

    @Test
    @DisplayName("An export only covers the vault of its own user")
    void exportsOnlyOwnEntries() throws IOException {
        User owner = newUser();
        User stranger = newUser();
        vaultService.create(owner, keyOf(owner, PASSWORD), "Mine", withFile(owner));
        vaultService.create(stranger, keyOf(stranger, PASSWORD), "Theirs", withFile(stranger));

        assertThat(manifestOf(export(owner))).contains("Mine").doesNotContain("Theirs");
    }

    private String manifestOf(byte[] archive) throws IOException {
        try (java.util.zip.ZipInputStream zip =
                     new java.util.zip.ZipInputStream(new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (BackupFormat.MANIFEST.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("The archive has no manifest");
    }

    private byte[] export(User owner) throws IOException {
        return export(owner, ARCHIVE_PASSWORD);
    }

    private byte[] export(User owner, String archivePassword) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        backupService.export(owner, keyOf(owner, PASSWORD), archivePassword, out);
        return out.toByteArray();
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey keyOf(User user, String password) {
        return keyDerivation.deriveMasterKey(password.toCharArray(), user.getKeySalt());
    }

    private List<NewField> withFile(User owner) {
        String stagingKey = fileStore.newStagingKey(owner);
        VaultService.StagingSession session = vaultService.openStaging(stagingKey);
        try (OutputStream out = session.upload().stream()) {
            out.write(FILE_BODY.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return List.of(
                NewField.text("Password", SECRET, true),
                NewField.stagedFile("Screenshot", new StagedFile(stagingKey, "shot.png", "image/png",
                        FILE_BODY.length(), session.fileKey()), false));
    }
}
