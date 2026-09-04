package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.storage.StorageProperties;
import dev.lockbox.user.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.UUID;

@Component
public class FieldFileStore {

    private final ObjectStorage storage;
    private final AesGcmCipher cipher;
    private final StorageProperties properties;

    public FieldFileStore(ObjectStorage storage, AesGcmCipher cipher, StorageProperties properties) {
        this.storage = storage;
        this.cipher = cipher;
        this.properties = properties;
    }

    public long maxSizeBytes() {
        return properties.maxAttachmentSize().toBytes();
    }

    public String store(User owner, StoredFile file, SecretKey dataKey) {
        long limit = maxSizeBytes();
        if (file.content().length > limit) {
            throw new FileTooLargeException(file.content().length, limit);
        }
        String storageKey = "%d/%s".formatted(owner.getId(), UUID.randomUUID());
        storage.put(storageKey, cipher.encrypt(file.content(), dataKey));
        return storageKey;
    }

    public StoredFile read(EntryField field, SecretKey dataKey) {
        byte[] content = cipher.decrypt(storage.get(field.getStorageKey()), dataKey);
        return new StoredFile(field.getFileName(), field.getContentType(), content);
    }

    public void remove(String storageKey) {
        storage.delete(storageKey);
    }
}
