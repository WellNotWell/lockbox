package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.crypto.CryptoProperties;
import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VaultServiceTest {

    private static final String SECRET = "SuperSecret123";

    private final EntryRepository repository = mock(EntryRepository.class);
    private final AesGcmCipher cipher = new AesGcmCipher();
    private final KeyEnvelope keyEnvelope = new KeyEnvelope(cipher);
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));

    private final VaultService service = new VaultService(repository, keyEnvelope, cipher);

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

    private List<NewField> fields() {
        return List.of(new NewField("URL", "postgres://prod", false), new NewField("Password", SECRET, true));
    }
}
