package dev.lockbox.vault;

import dev.lockbox.crypto.AesGcmCipher;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class VaultService {

    private final EntryRepository entryRepository;
    private final KeyEnvelope keyEnvelope;
    private final AesGcmCipher cipher;
    private final FieldFileStore fileStore;

    public VaultService(EntryRepository entryRepository, KeyEnvelope keyEnvelope, AesGcmCipher cipher,
                        FieldFileStore fileStore) {
        this.entryRepository = entryRepository;
        this.keyEnvelope = keyEnvelope;
        this.cipher = cipher;
        this.fileStore = fileStore;
    }

    public long maxFileSizeBytes() {
        return fileStore.maxSizeBytes();
    }

    @Transactional
    public Entry create(User owner, SecretKey masterKey, String title, List<NewField> fields) {
        SecretKey dataKey = keyEnvelope.newDataKey();

        Entry entry = new Entry();
        entry.setOwner(owner);
        entry.setTitle(title.trim());
        entry.setDataKey(keyEnvelope.wrap(dataKey, masterKey));
        entry.replaceFields(build(owner, entry, fields, dataKey));

        return entryRepository.save(entry);
    }

    @Transactional
    public Entry update(User owner, SecretKey masterKey, Long entryId, String title, List<NewField> fields) {
        Entry entry = require(owner, entryId);
        SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), masterKey);

        List<String> before = entry.storageKeys();
        List<EntryField> rebuilt = build(owner, entry, fields, dataKey);

        entry.setTitle(title.trim());
        entry.replaceFields(rebuilt);

        before.stream().filter(key -> !entry.storageKeys().contains(key)).forEach(fileStore::remove);
        return entry;
    }

    @Transactional(readOnly = true)
    public List<Entry> list(User owner) {
        return entryRepository.findByOwnerIdOrderByTitle(owner.getId());
    }

    @Transactional(readOnly = true)
    public DecryptedEntry open(User owner, SecretKey masterKey, Long entryId) {
        Entry entry = require(owner, entryId);
        SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), masterKey);

        List<DecryptedField> fields = entry.getFields().stream().map(field -> field.isFile()
                ? new DecryptedField(field.getId(), FieldKind.FILE, field.getLabel(), field.isSecret(), null,
                new FileInfo(field.getFileName(), field.getContentType(), field.getSizeBytes()))
                : new DecryptedField(field.getId(), FieldKind.TEXT, field.getLabel(), field.isSecret(),
                        new String(cipher.decrypt(field.getValue(), dataKey), StandardCharsets.UTF_8), null))
                .toList();

        return new DecryptedEntry(entry.getId(), entry.getTitle(), fields, entry.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public StoredFile openFile(User owner, SecretKey masterKey, Long entryId, Long fieldId) {
        Entry entry = require(owner, entryId);
        EntryField field = entry.getFields().stream()
                .filter(candidate -> candidate.isFile() && candidate.getId().equals(fieldId))
                .findFirst()
                .orElseThrow(() -> new FieldNotFoundException(fieldId));

        return fileStore.read(field, keyEnvelope.unwrap(entry.getDataKey(), masterKey));
    }

    @Transactional
    public void delete(User owner, Long entryId) {
        Entry entry = require(owner, entryId);
        List<String> storageKeys = entry.storageKeys();

        entryRepository.delete(entry);
        storageKeys.forEach(fileStore::remove);
    }

    private Entry require(User owner, Long entryId) {
        return entryRepository.findByIdAndOwnerId(entryId, owner.getId())
                .orElseThrow(() -> new EntryNotFoundException(entryId));
    }

    private List<EntryField> build(User owner, Entry entry, List<NewField> sources, SecretKey dataKey) {
        List<EntryField> result = new ArrayList<>();
        for (NewField source : sources) {
            result.add(switch (source.kind()) {
                case TEXT -> textField(source, dataKey);
                case FILE -> source.keptId() == null
                        ? uploadedField(owner, source, dataKey)
                        : keptField(entry, source);
            });
        }
        return result;
    }

    private EntryField textField(NewField source, SecretKey dataKey) {
        EntryField field = new EntryField();
        field.setKind(FieldKind.TEXT);
        field.setLabel(source.label().trim());
        field.setSecret(source.secret());
        field.setValue(cipher.encrypt(source.value().getBytes(StandardCharsets.UTF_8), dataKey));
        return field;
    }

    private EntryField uploadedField(User owner, NewField source, SecretKey dataKey) {
        StoredFile file = source.file();
        EntryField field = new EntryField();
        field.setKind(FieldKind.FILE);
        field.setLabel(source.label().trim());
        field.setSecret(source.secret());
        field.setFileName(file.fileName());
        field.setContentType(file.contentType());
        field.setSizeBytes((long) file.content().length);
        field.setStorageKey(fileStore.store(owner, file, dataKey));
        return field;
    }

    private EntryField keptField(Entry entry, NewField source) {
        EntryField field = entry.getFields().stream()
                .filter(candidate -> candidate.isFile() && candidate.getId().equals(source.keptId()))
                .findFirst()
                .orElseThrow(() -> new FieldNotFoundException(source.keptId()));

        field.setLabel(source.label().trim());
        field.setSecret(source.secret());
        return field;
    }
}
