package dev.lockbox.vault;

import dev.lockbox.crypto.ChunkedCipher;
import dev.lockbox.storage.MultipartUploadStream;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.storage.StorageException;
import dev.lockbox.storage.StorageProperties;
import dev.lockbox.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class FieldFileStore {

    static final String STAGING_PREFIX = "staging/";

    private static final Logger log = LoggerFactory.getLogger(FieldFileStore.class);

    private final ObjectStorage storage;
    private final ChunkedCipher cipher;
    private final StorageProperties properties;

    public FieldFileStore(ObjectStorage storage, ChunkedCipher cipher, StorageProperties properties) {
        this.storage = storage;
        this.cipher = cipher;
        this.properties = properties;
    }

    public long maxSizeBytes() {
        return properties.maxAttachmentSize().toBytes();
    }

    public String newStagingKey(User owner) {
        return "%s%d/%s".formatted(STAGING_PREFIX, owner.getId(), UUID.randomUUID());
    }

    public StagedUpload openStaging(String stagingKey, SecretKey dataKey) {
        MultipartUploadStream upload = storage.openForWriting(stagingKey);
        try {
            OutputStream encrypting = cipher.encryptingStream(upload, dataKey);
            return new StagedUpload(new LimitedOutputStream(encrypting, maxSizeBytes()), upload);
        } catch (IOException e) {
            upload.abort();
            throw new StorageException("Cannot start encrypting upload for " + stagingKey, e);
        }
    }

    public String promote(User owner, String stagingKey) {
        String storageKey = "%d/%s".formatted(owner.getId(), UUID.randomUUID());
        storage.copy(stagingKey, storageKey);
        storage.delete(stagingKey);
        return storageKey;
    }

    public void readInto(EntryField field, SecretKey dataKey, OutputStream target) throws IOException {
        readInto(field.getStorageKey(), dataKey, target);
    }

    public void readInto(String storageKey, SecretKey dataKey, OutputStream target) throws IOException {
        try (InputStream source = storage.openForReading(storageKey)) {
            cipher.decrypt(source, target, dataKey);
        }
    }

    public void remove(String storageKey) {
        storage.delete(storageKey);
    }

    public int sweepStaging(Duration ttl) {
        List<String> stale = storage.keysOlderThan(STAGING_PREFIX, Instant.now().minus(ttl));
        stale.forEach(storage::delete);
        if (!stale.isEmpty()) {
            log.info("Removed {} staged files nobody saved", stale.size());
        }
        return stale.size();
    }

    public record StagedUpload(OutputStream stream, MultipartUploadStream upload) {

        public void abort() {
            upload.abort();
        }
    }

    private static class LimitedOutputStream extends OutputStream {

        private final OutputStream target;
        private final long limit;
        private long written;

        LimitedOutputStream(OutputStream target, long limit) {
            this.target = target;
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            checkRoom(1);
            target.write(b);
        }

        @Override
        public void write(byte[] data, int offset, int length) throws IOException {
            checkRoom(length);
            target.write(data, offset, length);
        }

        @Override
        public void close() throws IOException {
            target.close();
        }

        private void checkRoom(int length) {
            written += length;
            if (written > limit) {
                throw new FileTooLargeException(written, limit);
            }
        }
    }
}
