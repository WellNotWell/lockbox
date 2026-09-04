package dev.lockbox.vault;

import dev.lockbox.storage.StorageProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StagingSweeper {

    private final FieldFileStore fileStore;
    private final StorageProperties properties;

    public StagingSweeper(FieldFileStore fileStore, StorageProperties properties) {
        this.fileStore = fileStore;
        this.properties = properties;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 1_800_000)
    public void sweep() {
        fileStore.sweepStaging(properties.stagingTtl());
    }
}
