package dev.lockbox.vault;

import dev.lockbox.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "data_key", nullable = false)
    private byte[] dataKey;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("sortOrder")
    private List<EntryField> fields = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void replaceFields(List<EntryField> newFields) {
        fields.removeIf(existing -> newFields.stream().noneMatch(kept -> kept == existing));
        for (int index = 0; index < newFields.size(); index++) {
            EntryField field = newFields.get(index);
            field.setEntry(this);
            field.setSortOrder(index);
            if (fields.stream().noneMatch(existing -> existing == field)) {
                fields.add(field);
            }
        }
        fields.sort(Comparator.comparingInt(EntryField::getSortOrder));
    }

    public List<String> storageKeys() {
        return fields.stream().filter(EntryField::isFile).map(EntryField::getStorageKey).toList();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public byte[] getDataKey() {
        return dataKey;
    }

    public void setDataKey(byte[] dataKey) {
        this.dataKey = dataKey;
    }

    public List<EntryField> getFields() {
        return fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
