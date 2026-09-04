package dev.lockbox.vault;

import dev.lockbox.crypto.ChunkedCipher;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FileFieldIT {

    private static final String PASSWORD = "correct horse battery";
    private static final String MARKER = "recovery code 4815162342";

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
    private FieldFileStore fileStore;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("The object in the bucket is unreadable and carries the chunked header")
    void storesCiphertextInTheBucket() throws IOException {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile(user, 1024));

        byte[] raw = storage.get(storageKeyOf(entry));

        assertThat(new String(raw, StandardCharsets.UTF_8)).doesNotContain(MARKER);
        assertThat(new String(raw, 0, 4, StandardCharsets.UTF_8)).isEqualTo("LBX1");
    }

    @Test
    @DisplayName("A file larger than one chunk survives the round trip byte for byte")
    void streamsFileLargerThanOneChunk() throws IOException {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        int size = 3 * ChunkedCipher.CHUNK_SIZE + 5000;
        byte[] original = content(size);
        Entry entry = vaultService.create(user, masterKey, "Big file", withFile(user, original));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        vaultService.writeFile(user, masterKey, entry.getId(), fileFieldId(entry), out);

        assertThat(out.toByteArray()).isEqualTo(original);
        assertThat(storage.get(storageKeyOf(entry)).length)
                .isEqualTo((int) new ChunkedCipher().encryptedLength(size));
    }

    @Test
    @DisplayName("A file spanning several upload parts survives the round trip")
    void streamsFileAcrossSeveralParts() throws IOException {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        byte[] original = content(12 * 1024 * 1024);
        Entry entry = vaultService.create(user, masterKey, "Huge file", withFile(user, original));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        vaultService.writeFile(user, masterKey, entry.getId(), fileFieldId(entry), out);

        assertThat(out.toByteArray()).isEqualTo(original);
    }

    @Test
    @DisplayName("Each file field carries its own key, wrapped in the key of its entry")
    void keepsPerFileKeys() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile(user, 1024));

        byte[] dataKey = jdbcTemplate.queryForObject(
                "select data_key from entry_fields where entry_id = ? and kind = 'FILE'",
                byte[].class, entry.getId());

        assertThat(dataKey).isNotNull().hasSizeGreaterThan(32);
    }

    @Test
    @DisplayName("Staging is emptied once the file becomes a field")
    void promotesOutOfStaging() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile(user, 1024));

        assertThat(storageKeyOf(entry)).doesNotStartWith("staging/");
        assertThat(storage.keysOlderThan("staging/", java.time.Instant.now().plusSeconds(60))).isEmpty();
    }

    @Test
    @DisplayName("A file nobody saved is swept from staging")
    void sweepsAbandonedStaging() throws IOException {
        User user = newUser();
        String stagingKey = fileStore.newStagingKey(user);
        try (OutputStream out = fileStore.openStaging(stagingKey, newFileKey()).stream()) {
            out.write(content(2048));
        }

        assertThat(storage.keysOlderThan("staging/", java.time.Instant.now().plusSeconds(60)))
                .contains(stagingKey);

        fileStore.sweepStaging(Duration.ZERO);

        assertThat(storage.keysOlderThan("staging/", java.time.Instant.now().plusSeconds(60)))
                .doesNotContain(stagingKey);
    }

    @Test
    @DisplayName("A tampered object is refused on download")
    void refusesTamperedObject() throws IOException {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile(user, 4096));

        String key = storageKeyOf(entry);
        byte[] raw = storage.get(key);
        raw[raw.length / 2] ^= 0x01;
        storage.put(key, raw);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> vaultService.writeFile(user, masterKey, entry.getId(), fileFieldId(entry), out))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Removing the file row deletes the object from the bucket")
    void deletesObjectWhenRowIsRemoved() {
        User user = newUser();
        SecretKey masterKey = masterKeyOf(user);
        Entry entry = vaultService.create(user, masterKey, "Recovery codes", withFile(user, 1024));
        String storageKey = storageKeyOf(entry);

        vaultService.update(user, masterKey, entry.getId(), "Recovery codes",
                List.of(NewField.text("Service", "GitHub", false)));

        assertThatThrownBy(() -> storage.get(storageKey)).isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("Another user neither opens the file nor learns that it exists")
    void isolatesUsers() {
        User owner = newUser();
        Entry entry = vaultService.create(owner, masterKeyOf(owner), "Recovery codes", withFile(owner, 1024));
        Long fieldId = fileFieldId(entry);
        User stranger = newUser();

        assertThatThrownBy(() -> vaultService.writeFile(stranger, masterKeyOf(stranger), entry.getId(),
                fieldId, new ByteArrayOutputStream()))
                .isInstanceOf(EntryNotFoundException.class);
    }

    @Test
    @DisplayName("A wrong master password cannot open an own file")
    void rejectsWrongMasterPassword() {
        User user = newUser();
        Entry entry = vaultService.create(user, masterKeyOf(user), "Recovery codes", withFile(user, 1024));
        Long fieldId = fileFieldId(entry);
        SecretKey wrongKey = keyDerivation.deriveMasterKey("wrong password".toCharArray(), user.getKeySalt());

        assertThatThrownBy(() -> vaultService.writeFile(user, wrongKey, entry.getId(), fieldId,
                new ByteArrayOutputStream()))
                .isInstanceOf(DecryptionException.class);
    }

    private User newUser() {
        return registrationService.register("user-" + UUID.randomUUID(), PASSWORD);
    }

    private SecretKey masterKeyOf(User user) {
        return keyDerivation.deriveMasterKey(PASSWORD.toCharArray(), user.getKeySalt());
    }

    private SecretKey newFileKey() {
        return vaultService.openStaging("staging/probe/" + UUID.randomUUID()).fileKey();
    }

    private List<NewField> withFile(User user, int size) {
        return withFile(user, content(size));
    }

    private List<NewField> withFile(User user, byte[] payload) {
        String stagingKey = fileStore.newStagingKey(user);
        VaultService.StagingSession session = vaultService.openStaging(stagingKey);
        try (OutputStream out = session.upload().stream()) {
            out.write(payload);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return List.of(
                NewField.text("Service", "GitHub", false),
                NewField.stagedFile("Screenshot",
                        new StagedFile(stagingKey, "recovery.png", "image/png", payload.length,
                                session.fileKey()), true));
    }

    private static byte[] content(int size) {
        byte[] data = MARKER.repeat(size / MARKER.length() + 1).getBytes(StandardCharsets.UTF_8);
        return java.util.Arrays.copyOf(data, size);
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
