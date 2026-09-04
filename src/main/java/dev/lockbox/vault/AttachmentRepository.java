package dev.lockbox.vault;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByEntryIdOrderByCreatedAt(Long entryId);

    Optional<Attachment> findByIdAndEntryOwnerId(Long id, Long ownerId);
}
