package dev.lockbox.ui;

import com.vaadin.flow.server.streams.DownloadHandler;
import dev.lockbox.vault.StagedFile;
import dev.lockbox.vault.VaultService;

interface FileAccess {

    String newStagingKey();

    VaultService.StagingSession openStaging(String stagingKey);

    DownloadHandler download(Long fieldId, boolean inline);

    DownloadHandler previewStaged(StagedFile staged);
}
