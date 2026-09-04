package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.crypto.CryptoProperties;
import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.storage.StorageProperties;
import dev.lockbox.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultServiceTest {

    private static final String SECRET = "SuperSecret123";
    private static final byte[] PASSPORT = "PNG with my passport number".getBytes(StandardCharsets.UTF_8);

    private final EntryRepository repository = mock(EntryRepository.class);
    private final AesGcmCipher cipher = new AesGcmCipher();
    private final KeyEnvelope keyEnvelope = new KeyEnvelope(cipher);
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));

    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final FieldFileStore fileStore = new FieldFileStore(storage, cipher,
            new StorageProperties("http://localhost:9000", "key", "secret", "bucket", DataSize.ofMegabytes(25)));

    private final VaultService service = new VaultService(repository, keyEnvelope, cipher, fileStore);

    private SecretKey masterKey;
    private User owner;

    @BeforeEach
    void setUp() {
        masterKey = keyDerivation.deriveMasterKey("master password".toCharArray(), keyDerivation.newSalt());
        owner = mock(User.class);
        when(owner.getId()).thenReturn(1L);
        when(repository.save(any(Entry.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Field values are stored encrypted, the plain text never reaches the entity")
    void storesEncryptedValues() {
        Entry entry = service.create(owner, masterKey, "Prod database", fields());

        byte[] stored = entry.getFields().get(1).getValue();

        assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain(SECRET);
        assertThat(stored).hasSizeGreaterThan(SECRET.length());
    }

    @Test
    @DisplayName("Labels stay readable while values do not")
    void keepsLabelsReadable() {
        Entry entry = service.create(owner, masterKey, "Prod database", fields());

        assertThat(entry.getFields()).extracting(EntryField::getLabel).containsExactly("URL", "Password");
        assertThat(entry.getFields().get(1).isSecret()).isTrue();
    }

    @Test
    @DisplayName("Every entry gets its own data key")
    void generatesOwnDataKeyPerEntry() {
        Entry first = service.create(owner, masterKey, "First", fields());
        Entry second = service.create(owner, masterKey, "Second", fields());

        assertThat(first.getDataKey()).isNotEqualTo(second.getDataKey());
        assertThat(keyEnvelope.unwrap(first.getDataKey(), masterKey).getEncoded())
                .isNotEqualTo(keyEnvelope.unwrap(second.getDataKey(), masterKey).getEncoded());
    }

    @Test
    @DisplayName("Opening an entry with the right master key returns the original values")
    void opensEntry() {
        Entry entry = service.create(owner, masterKey, "Prod database", fields());
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.of(entry));

        DecryptedEntry opened = service.open(owner, masterKey, 42L);

        assertThat(opened.title()).isEqualTo("Prod database");
        assertThat(opened.fields()).extracting(DecryptedField::value)
                .containsExactly("postgres://prod", SECRET);
    }

    @Test
    @DisplayName("Another master key cannot open the entry")
    void rejectsForeignMasterKey() {
        Entry entry = service.create(owner, masterKey, "Prod database", fields());
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.of(entry));
        SecretKey otherKey = keyDerivation.deriveMasterKey("another password".toCharArray(), keyDerivation.newSalt());

        assertThatThrownBy(() -> service.open(owner, otherKey, 42L)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("An entry of another user is reported as missing, without telling it exists")
    void hidesForeignEntries() {
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(owner, masterKey, 42L))
                .isInstanceOf(EntryNotFoundException.class)
                .hasMessageContaining("does not exist or belongs to another user");
    }

    @Test
    @DisplayName("A file field is encrypted before it reaches the storage")
    void encryptsFileBeforeUpload() {
        service.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), true)));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.captor();
        verify(storage).put(anyString(), stored.capture());
        assertThat(new String(stored.getValue(), StandardCharsets.UTF_8)).doesNotContain("passport number");
        assertThat(stored.getValue()).hasSizeGreaterThan(PASSPORT.length);
    }

    @Test
    @DisplayName("A file field keeps name, type and size readable, the content does not")
    void keepsFileMetadataReadable() {
        Entry entry = service.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), true)));

        EntryField field = entry.getFields().getFirst();
        assertThat(field.getKind()).isEqualTo(FieldKind.FILE);
        assertThat(field.getLabel()).isEqualTo("Passport");
        assertThat(field.getFileName()).isEqualTo("passport.png");
        assertThat(field.getContentType()).isEqualTo("image/png");
        assertThat(field.getSizeBytes()).isEqualTo(PASSPORT.length);
        assertThat(field.getValue()).isNull();
        assertThat(field.isSecret()).isTrue();
    }

    @Test
    @DisplayName("Text and file fields share one ordered list")
    void mixesTextAndFileFields() {
        Entry entry = service.create(owner, masterKey, "Documents", List.of(
                NewField.text("URL", "postgres://prod", false),
                NewField.uploadedFile("Passport", passport(), true),
                NewField.text("Password", SECRET, true)));

        assertThat(entry.getFields()).extracting(EntryField::getLabel)
                .containsExactly("URL", "Passport", "Password");
        assertThat(entry.getFields()).extracting(EntryField::getKind)
                .containsExactly(FieldKind.TEXT, FieldKind.FILE, FieldKind.TEXT);
        assertThat(entry.getFields()).extracting(EntryField::getSortOrder).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("A file too large is rejected before anything is written")
    void rejectsOversizedFile() {
        VaultService strict = new VaultService(repository, keyEnvelope, cipher,
                new FieldFileStore(storage, cipher, new StorageProperties("http://localhost:9000", "key",
                        "secret", "bucket", DataSize.ofBytes(10))));

        assertThatThrownBy(() -> strict.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), false))))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessageContaining("exceeds the limit");

        verify(storage, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("Saving an entry again keeps the stored file instead of uploading it twice")
    void keepsStoredFileOnUpdate() {
        Entry entry = service.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), false)));
        EntryField file = entry.getFields().getFirst();
        setId(file, 7L);
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.of(entry));

        service.update(owner, masterKey, 42L, "Documents", List.of(
                NewField.keptFile(7L, "Passport scan", false)));

        verify(storage).put(anyString(), any());
        verify(storage, never()).delete(anyString());
        assertThat(entry.getFields()).hasSize(1);
        assertThat(entry.getFields().getFirst().getStorageKey()).isEqualTo(file.getStorageKey());
        assertThat(entry.getFields().getFirst().getLabel()).isEqualTo("Passport scan");
    }

    @Test
    @DisplayName("Removing a file row deletes the stored object")
    void deletesObjectWhenRowIsRemoved() {
        Entry entry = service.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), false)));
        String storageKey = entry.getFields().getFirst().getStorageKey();
        setId(entry.getFields().getFirst(), 7L);
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.of(entry));

        service.update(owner, masterKey, 42L, "Documents", List.of(NewField.text("Note", "kept", false)));

        verify(storage).delete(storageKey);
        assertThat(entry.getFields()).extracting(EntryField::getLabel).containsExactly("Note");
    }

    @Test
    @DisplayName("Deleting an entry removes the objects of its file fields")
    void deletesObjectsWithEntry() {
        Entry entry = service.create(owner, masterKey, "Documents", List.of(
                NewField.uploadedFile("Passport", passport(), false)));
        String storageKey = entry.getFields().getFirst().getStorageKey();
        when(repository.findByIdAndOwnerId(any(), anyLong())).thenReturn(Optional.of(entry));

        service.delete(owner, 42L);

        verify(repository).delete(entry);
        verify(storage).delete(storageKey);
    }

    private static void setId(EntryField field, Long id) {
        try {
            var idField = EntryField.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(field, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private StoredFile passport() {
        return new StoredFile("passport.png", "image/png", PASSPORT);
    }

    private List<NewField> fields() {
        return List.of(NewField.text("URL", "postgres://prod", false), NewField.text("Password", SECRET, true));
    }
}
