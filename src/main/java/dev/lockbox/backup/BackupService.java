package dev.lockbox.backup;

import tools.jackson.databind.ObjectMapper;
import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.storage.ObjectStorage;
import dev.lockbox.user.User;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.EntryField;
import dev.lockbox.vault.EntryRepository;
import dev.lockbox.vault.FieldKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final EntryRepository entryRepository;
    private final ObjectStorage storage;
    private final KeyDerivation keyDerivation;
    private final KeyEnvelope keyEnvelope;
    private final ObjectMapper mapper = new ObjectMapper();

    public BackupService(EntryRepository entryRepository, ObjectStorage storage,
                         KeyDerivation keyDerivation, KeyEnvelope keyEnvelope) {
        this.entryRepository = entryRepository;
        this.storage = storage;
        this.keyDerivation = keyDerivation;
        this.keyEnvelope = keyEnvelope;
    }

    @Transactional(readOnly = true)
    public void export(User owner, SecretKey masterKey, String archivePassword, OutputStream target)
            throws IOException {
        Base64.Encoder base64 = Base64.getEncoder();
        byte[] archiveSalt = keyDerivation.newSalt();
        SecretKey archiveKey = keyDerivation.deriveMasterKey(archivePassword.toCharArray(), archiveSalt);
        List<Entry> entries = entryRepository.findByOwnerIdOrderByTitle(owner.getId());
        Map<String, String> filesToCopy = new HashMap<>();
        List<BackupFormat.BackupEntry> exported = new ArrayList<>();

        for (Entry entry : entries) {
            List<BackupFormat.BackupField> fields = new ArrayList<>();
            for (EntryField field : entry.getFields()) {
                String inArchive = null;
                if (field.isFile()) {
                    inArchive = UUID.randomUUID().toString();
                    filesToCopy.put(inArchive, field.getStorageKey());
                }
                fields.add(new BackupFormat.BackupField(
                        field.getKind().name(), field.getLabel(), field.isSecret(), field.getSortOrder(),
                        field.getValue() == null ? null : base64.encodeToString(field.getValue()),
                        field.getFileName(), field.getContentType(), field.getSizeBytes(),
                        field.getDataKey() == null ? null : base64.encodeToString(field.getDataKey()),
                        inArchive));
            }
            SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), masterKey);
            exported.add(new BackupFormat.BackupEntry(entry.getTitle(),
                    base64.encodeToString(keyEnvelope.wrap(dataKey, archiveKey)),
                    entry.getCreatedAt().toString(), entry.getUpdatedAt().toString(), fields));
        }

        BackupFormat.Manifest manifest = new BackupFormat.Manifest(BackupFormat.VERSION,
                owner.getUsername(), java.time.Instant.now().toString(),
                base64.encodeToString(archiveSalt), exported);

        try (ZipOutputStream zip = new ZipOutputStream(target)) {
            zip.putNextEntry(new ZipEntry(BackupFormat.MANIFEST));
            zip.write(mapper.writeValueAsBytes(manifest));
            zip.closeEntry();

            for (Map.Entry<String, String> file : filesToCopy.entrySet()) {
                zip.putNextEntry(new ZipEntry(BackupFormat.FILES + file.getKey()));
                storage.readInto(file.getValue(), zip);
                zip.closeEntry();
            }
        }
        log.info("Exported {} entries and {} files", exported.size(), filesToCopy.size());
    }

    @Transactional
    public int restore(User owner, SecretKey currentMasterKey, String archivePassword, InputStream source)
            throws IOException {
        Base64.Decoder base64 = Base64.getDecoder();
        BackupFormat.Manifest manifest = null;
        Map<String, byte[]> files = new HashMap<>();

        try (ZipInputStream zip = new ZipInputStream(source)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (BackupFormat.MANIFEST.equals(entry.getName())) {
                    manifest = mapper.readValue(zip.readAllBytes(), BackupFormat.Manifest.class);
                } else if (entry.getName().startsWith(BackupFormat.FILES)) {
                    files.put(entry.getName().substring(BackupFormat.FILES.length()), zip.readAllBytes());
                }
            }
        }
        if (manifest == null) {
            throw new BackupFormatException("The archive has no " + BackupFormat.MANIFEST);
        }
        if (manifest.version() != BackupFormat.VERSION) {
            throw new BackupFormatException("Unsupported backup version " + manifest.version());
        }

        SecretKey archiveKey = keyDerivation.deriveMasterKey(archivePassword.toCharArray(),
                base64.decode(manifest.salt()));

        int restored = 0;
        for (BackupFormat.BackupEntry source1 : manifest.entries()) {
            SecretKey dataKey = keyEnvelope.unwrap(base64.decode(source1.dataKey()), archiveKey);

            Entry entry = new Entry();
            entry.setOwner(owner);
            entry.setTitle(source1.title());
            entry.setDataKey(keyEnvelope.wrap(dataKey, currentMasterKey));

            List<EntryField> fields = new ArrayList<>();
            for (BackupFormat.BackupField field : source1.fields()) {
                EntryField restoredField = new EntryField();
                restoredField.setKind(FieldKind.valueOf(field.kind()));
                restoredField.setLabel(field.label());
                restoredField.setSecret(field.secret());
                if (field.value() != null) {
                    restoredField.setValue(base64.decode(field.value()));
                }
                if (restoredField.isFile()) {
                    byte[] content = files.get(field.fileInArchive());
                    if (content == null) {
                        throw new BackupFormatException("The archive misses file " + field.fileName());
                    }
                    String storageKey = "%d/%s".formatted(owner.getId(), UUID.randomUUID());
                    storage.put(storageKey, content);
                    restoredField.setStorageKey(storageKey);
                    restoredField.setFileName(field.fileName());
                    restoredField.setContentType(field.contentType());
                    restoredField.setSizeBytes(field.sizeBytes());
                    restoredField.setDataKey(base64.decode(field.fileKey()));
                }
                fields.add(restoredField);
            }
            entry.replaceFields(fields);
            entryRepository.save(entry);
            restored++;
        }
        log.info("Restored {} entries and {} files", restored, files.size());
        return restored;
    }
}
