package dev.lockbox.vault;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByOwnerIdOrderByTitle(Long ownerId);

    Optional<Entry> findByIdAndOwnerId(Long id, Long ownerId);
}
