package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.crypto.CryptoProperties;
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

class AttachmentServiceTest {

    private static final byte[] SCREENSHOT = "PNG screenshot with my passport number".getBytes(StandardCharsets.UTF_8);

    private final AttachmentRepository attachmentRepository = mock(AttachmentRepository.class);
    private final EntryRepository entryRepository = mock(EntryRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final AesGcmCipher cipher = new AesGcmCipher();
    private final KeyEnvelope keyEnvelope = new KeyEnvelope(cipher);
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));

    private AttachmentService service;
    private SecretKey masterKey;
    private User owner;
    private Entry entry;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(attachmentRepository, entryRepository, storage, keyEnvelope, cipher,
                new StorageProperties("http://localhost:9000", "key", "secret", "bucket", DataSize.ofMegabytes(25)));

        masterKey = keyDerivation.deriveMasterKey("master password".toCharArray(), keyDerivation.newSalt());
        owner = mock(User.class);
        when(owner.getId()).thenReturn(1L);

        entry = new Entry();
        entry.setOwner(owner);
        entry.setTitle("Documents");
        entry.setDataKey(keyEnvelope.wrap(keyEnvelope.newDataKey(), masterKey));

        when(entryRepository.findByIdAndOwnerId(anyLong(), anyLong())).thenReturn(Optional.of(entry));
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("File is encrypted before it reaches the storage")
    void encryptsBeforeUpload() {
        service.upload(owner, masterKey, 1L, new StoredFile("passport.png", "image/png", SCREENSHOT));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.captor();
        verify(storage).put(anyString(), stored.capture());
        assertThat(new String(stored.getValue(), StandardCharsets.UTF_8)).doesNotContain("passport number");
        assertThat(stored.getValue()).hasSizeGreaterThan(SCREENSHOT.length);
    }

    @Test
    @DisplayName("File name and size stay readable, the content does not")
    void keepsMetadataReadable() {
        Attachment attachment = service.upload(owner, masterKey, 1L,
                new StoredFile("passport.png", "image/png", SCREENSHOT));

        assertThat(attachment.getFileName()).isEqualTo("passport.png");
        assertThat(attachment.getContentType()).isEqualTo("image/png");
        assertThat(attachment.getSizeBytes()).isEqualTo(SCREENSHOT.length);
        assertThat(attachment.getStorageKey()).startsWith("1/");
    }

    @Test
    @DisplayName("Downloading gives the original bytes back")
    void decryptsOnDownload() {
        Attachment attachment = service.upload(owner, masterKey, 1L,
                new StoredFile("passport.png", "image/png", SCREENSHOT));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.captor();
        verify(storage).put(anyString(), stored.capture());
        when(storage.get(attachment.getStorageKey())).thenReturn(stored.getValue());
        when(attachmentRepository.findByIdAndEntryOwnerId(anyLong(), anyLong())).thenReturn(Optional.of(attachment));

        StoredFile downloaded = service.download(owner, masterKey, 7L);

        assertThat(downloaded.content()).isEqualTo(SCREENSHOT);
        assertThat(downloaded.fileName()).isEqualTo("passport.png");
    }

    @Test
    @DisplayName("Oversized file is rejected before anything is written")
    void rejectsOversizedFile() {
        AttachmentService strict = new AttachmentService(attachmentRepository, entryRepository, storage,
                keyEnvelope, cipher,
                new StorageProperties("http://localhost:9000", "key", "secret", "bucket", DataSize.ofBytes(10)));

        assertThatThrownBy(() -> strict.upload(owner, masterKey, 1L,
                new StoredFile("passport.png", "image/png", SCREENSHOT)))
                .isInstanceOf(AttachmentTooLargeException.class)
                .hasMessageContaining("exceeds the limit");

        verify(storage, never()).put(anyString(), any());
    }

    @Test
    @DisplayName("An attachment of another user is reported as missing")
    void hidesForeignAttachments() {
        when(attachmentRepository.findByIdAndEntryOwnerId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.download(owner, masterKey, 7L))
                .isInstanceOf(AttachmentNotFoundException.class)
                .hasMessageContaining("belongs to another user");
    }

    @Test
    @DisplayName("Deleting removes both the row and the stored object")
    void deletesRowAndObject() {
        Attachment attachment = service.upload(owner, masterKey, 1L,
                new StoredFile("passport.png", "image/png", SCREENSHOT));
        when(attachmentRepository.findByIdAndEntryOwnerId(anyLong(), anyLong())).thenReturn(Optional.of(attachment));

        service.delete(owner, 7L);

        verify(attachmentRepository).delete(attachment);
        verify(storage).delete(attachment.getStorageKey());
    }
}
