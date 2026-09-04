package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.storage.StorageProperties;
import dev.lockbox.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final EntryRepository entryRepository;
    private final ObjectStorage storage;
    private final KeyEnvelope keyEnvelope;
    private final AesGcmCipher cipher;
    private final StorageProperties properties;

    public AttachmentService(AttachmentRepository attachmentRepository,
                             EntryRepository entryRepository,
                             ObjectStorage storage,
                             KeyEnvelope keyEnvelope,
                             AesGcmCipher cipher,
                             StorageProperties properties) {
        this.attachmentRepository = attachmentRepository;
        this.entryRepository = entryRepository;
        this.storage = storage;
        this.keyEnvelope = keyEnvelope;
        this.cipher = cipher;
        this.properties = properties;
    }

    @Transactional
    public Attachment upload(User owner, SecretKey masterKey, Long entryId, StoredFile file) {
        long limit = properties.maxAttachmentSize().toBytes();
        if (file.content().length > limit) {
            throw new AttachmentTooLargeException(file.content().length, limit);
        }

        Entry entry = entryRepository.findByIdAndOwnerId(entryId, owner.getId())
                .orElseThrow(() -> new EntryNotFoundException(entryId));
        SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), masterKey);

        String storageKey = "%d/%s".formatted(entryId, UUID.randomUUID());
        storage.put(storageKey, cipher.encrypt(file.content(), dataKey));

        Attachment attachment = new Attachment();
        attachment.setEntry(entry);
        attachment.setFileName(file.fileName());
        attachment.setContentType(file.contentType());
        attachment.setSizeBytes(file.content().length);
        attachment.setStorageKey(storageKey);

        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(User owner, Long entryId) {
        entryRepository.findByIdAndOwnerId(entryId, owner.getId())
                .orElseThrow(() -> new EntryNotFoundException(entryId));
        return attachmentRepository.findByEntryIdOrderByCreatedAt(entryId);
    }

    @Transactional(readOnly = true)
    public StoredFile download(User owner, SecretKey masterKey, Long attachmentId) {
        Attachment attachment = require(owner, attachmentId);
        SecretKey dataKey = keyEnvelope.unwrap(attachment.getEntry().getDataKey(), masterKey);

        byte[] content = cipher.decrypt(storage.get(attachment.getStorageKey()), dataKey);
        return new StoredFile(attachment.getFileName(), attachment.getContentType(), content);
    }

    @Transactional
    public void delete(User owner, Long attachmentId) {
        Attachment attachment = require(owner, attachmentId);
        attachmentRepository.delete(attachment);
        storage.delete(attachment.getStorageKey());
    }

    private Attachment require(User owner, Long attachmentId) {
        return attachmentRepository.findByIdAndEntryOwnerId(attachmentId, owner.getId())
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
    }
}
