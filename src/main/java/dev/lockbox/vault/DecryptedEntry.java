package dev.lockbox.vault;

import java.time.Instant;
import java.util.List;

public record DecryptedEntry(Long id, String title, List<DecryptedField> fields, Instant updatedAt) {
}
