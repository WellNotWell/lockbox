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
class AttachmentIT {

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
    private AttachmentService attachmentService;

    @Autowired
    private ObjectStorage storage;

    @Test
    @DisplayName("The object stored in the bucket is unreadable")
    void storesCiphertextInTheBucket() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = newEntry(user, masterKey);

        Attachment attachment = attachmentService.upload(user, masterKey, entry.getId(), file());

        byte[] raw = storage.get(attachment.getStorageKey());
        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain("4815162342");
        assertThat(raw).hasSize(CONTENT.length() + 28);
    }

    @Test
    @DisplayName("The owner downloads the original file back")
    void downloadsOriginalFile() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = newEntry(user, masterKey);
        Attachment attachment = attachmentService.upload(user, masterKey, entry.getId(), file());

        StoredFile downloaded = attachmentService.download(user, masterKey, attachment.getId());

        assertThat(downloaded.fileName()).isEqualTo("recovery.png");
        assertThat(downloaded.contentType()).isEqualTo("image/png");
        assertThat(new String(downloaded.content(), StandardCharsets.UTF_8)).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("Attachments are listed for their entry only")
    void listsAttachmentsOfEntry() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = newEntry(user, masterKey);
        Entry other = newEntry(user, masterKey);
        attachmentService.upload(user, masterKey, entry.getId(), file());

        assertThat(attachmentService.list(user, entry.getId())).extracting(Attachment::getFileName)
                .containsExactly("recovery.png");
        assertThat(attachmentService.list(user, other.getId())).isEmpty();
    }

    @Test
    @DisplayName("Another user neither downloads nor deletes the attachment")
    void isolatesUsers() {
        User owner = newUser();
        SecretKey ownerKey = masterKeyOf(owner);
        Entry entry = newEntry(owner, ownerKey);
        Attachment attachment = attachmentService.upload(owner, ownerKey, entry.getId(), file());
        User stranger = newUser();

        assertThatThrownBy(() -> attachmentService.download(stranger, masterKeyOf(stranger), attachment.getId()))
                .isInstanceOf(AttachmentNotFoundException.class);
        assertThatThrownBy(() -> attachmentService.delete(stranger, attachment.getId()))
                .isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    @DisplayName("A wrong master password cannot open an own attachment")
    void rejectsWrongMasterPassword() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = newEntry(user, masterKey);
        Attachment attachment = attachmentService.upload(user, masterKey, entry.getId(), file());
        SecretKey wrongKey = keyDerivation.deriveMasterKey("wrong password".toCharArray(), user.getKeySalt());

        assertThatThrownBy(() -> attachmentService.download(user, wrongKey, attachment.getId()))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Deleting an attachment removes the stored object as well")
    void deletesStoredObject() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = newEntry(user, masterKey);
        Attachment attachment = attachmentService.upload(user, masterKey, entry.getId(), file());

        attachmentService.delete(user, attachment.getId());

        assertThat(attachmentService.list(user, entry.getId())).isEmpty();
        assertThatThrownBy(() -> storage.get(attachment.getStorageKey())).isInstanceOf(StorageException.class);
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey masterKeyOf(User user) {
        return keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt());
    }

    private Entry newEntry(User user, SecretKey masterKey) {
        return vaultService.create(user, masterKey, "Recovery codes",
                List.of(new NewField("Service", "GitHub", false)));
    }

    private StoredFile file() {
        return new StoredFile("recovery.png", "image/png", CONTENT.getBytes(StandardCharsets.UTF_8));
    }
}
