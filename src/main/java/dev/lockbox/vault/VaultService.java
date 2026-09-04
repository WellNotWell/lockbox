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

    public VaultService(EntryRepository entryRepository, KeyEnvelope keyEnvelope, AesGcmCipher cipher) {
        this.entryRepository = entryRepository;
        this.keyEnvelope = keyEnvelope;
        this.cipher = cipher;
    }

    @Transactional
    public Entry create(User owner, SecretKey masterKey, String title, List<NewField> fields) {
        SecretKey dataKey = keyEnvelope.newDataKey();

        Entry entry = new Entry();
        entry.setOwner(owner);
        entry.setTitle(title.trim());
        entry.setDataKey(keyEnvelope.wrap(dataKey, masterKey));
        entry.replaceFields(encrypt(fields, dataKey));

        return entryRepository.save(entry);
    }

    @Transactional
    public Entry update(User owner, SecretKey masterKey, Long entryId, String title, List<NewField> fields) {
        Entry entry = require(owner, entryId);
        SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), masterKey);

        entry.setTitle(title.trim());
        entry.replaceFields(encrypt(fields, dataKey));

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

        List<DecryptedField> fields = entry.getFields().stream()
                .map(field -> new DecryptedField(field.getId(), field.getLabel(),
                        new String(cipher.decrypt(field.getValue(), dataKey), StandardCharsets.UTF_8),
                        field.isSecret()))
                .toList();

        return new DecryptedEntry(entry.getId(), entry.getTitle(), fields, entry.getUpdatedAt());
    }

    @Transactional
    public void delete(User owner, Long entryId) {
        entryRepository.delete(require(owner, entryId));
    }

    private Entry require(User owner, Long entryId) {
        return entryRepository.findByIdAndOwnerId(entryId, owner.getId())
                .orElseThrow(() -> new EntryNotFoundException(entryId));
    }

    private List<EntryField> encrypt(List<NewField> fields, SecretKey dataKey) {
        List<EntryField> encrypted = new ArrayList<>();
        for (NewField source : fields) {
            EntryField field = new EntryField();
            field.setLabel(source.label().trim());
            field.setSecret(source.secret());
            field.setValue(cipher.encrypt(source.value().getBytes(StandardCharsets.UTF_8), dataKey));
            encrypted.add(field);
        }
        return encrypted;
    }
}
