package dev.lockbox.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByOwnerIdOrderByTitle(Long ownerId);

    @Query("""
            select distinct e from Entry e left join e.fields f
            where e.owner.id = :ownerId
              and (lower(e.title) like :pattern
                   or lower(f.label) like :pattern
                   or lower(coalesce(f.fileName, '')) like :pattern)
            order by e.title
            """)
    List<Entry> search(@Param("ownerId") Long ownerId, @Param("pattern") String pattern);

    Optional<Entry> findByIdAndOwnerId(Long id, Long ownerId);
}
