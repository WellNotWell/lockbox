package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import dev.lockbox.security.CurrentUser;
import com.vaadin.flow.server.streams.DownloadHandler;
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.FileInfo;
import dev.lockbox.vault.StagedFile;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.VaultService;
import jakarta.annotation.security.PermitAll;

import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PermitAll
public class EntriesView extends VerticalLayout implements HasDynamicTitle {

    private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final VaultService vaultService;
    private final CurrentUser currentUser;

    private final Grid<Entry> grid = new Grid<>();
    private final Paragraph empty = new Paragraph();

    public EntriesView(VaultService vaultService, CurrentUser currentUser) {
        this.vaultService = vaultService;
        this.currentUser = currentUser;

        setSizeFull();
        setPadding(true);

        Button create = new Button(Translations.of("entries.new"), event -> openDialog(null));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(create);
        toolbar.setWidthFull();

        grid.addColumn(Entry::getTitle).setHeader(Translations.of("entry.title")).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(entry -> UPDATED_AT.format(entry.getUpdatedAt().atZone(ZoneId.systemDefault())))
                .setHeader(Translations.of("entries.updated")).setAutoWidth(true);
        grid.addColumn(entry -> entry.getFields().size())
                .setHeader(Translations.of("entries.fieldCount")).setAutoWidth(true);
        grid.asSingleSelect().addValueChangeListener(event -> {
            if (event.getValue() != null) {
                open(event.getValue());
                grid.deselectAll();
            }
        });
        grid.setSizeFull();

        empty.setText(Translations.of("entries.empty"));
        empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        add(toolbar, empty, grid);
        expand(grid);
        refresh();
    }

    private void refresh() {
        List<Entry> entries = vaultService.list(currentUser.require());
        grid.setItems(entries);
        grid.setVisible(!entries.isEmpty());
        empty.setVisible(entries.isEmpty());
    }

    private void open(Entry entry) {
        openDialog(vaultService.open(currentUser.require(), currentUser.masterKey(), entry.getId()));
    }

    private void openDialog(DecryptedEntry existing) {
        EntryDialog dialog = new EntryDialog(existing,
                vaultService.maxFileSizeBytes(),
                fileAccess(existing),
                draft -> {
                    if (existing == null) {
                        vaultService.create(currentUser.require(), currentUser.masterKey(),
                                draft.title(), draft.fields());
                    } else {
                        vaultService.update(currentUser.require(), currentUser.masterKey(), existing.id(),
                                draft.title(), draft.fields());
                    }
                    refresh();
                    Notifications.success(Translations.of("entries.saved"));
                },
                () -> {
                    if (existing != null) {
                        vaultService.delete(currentUser.require(), existing.id());
                        refresh();
                    }
                });
        dialog.open();
    }

    private FileAccess fileAccess(DecryptedEntry existing) {
        return new FileAccess() {

            @Override
            public String newStagingKey() {
                return vaultService.newStagingKey(currentUser.require());
            }

            @Override
            public VaultService.StagingSession openStaging(String stagingKey) {
                return vaultService.openStaging(stagingKey);
            }

            @Override
            public DownloadHandler previewStaged(StagedFile staged) {
                return event -> {
                    event.inline(staged.fileName());
                    event.setContentType(previewContentType(staged.contentType()));
                    event.setContentLength(staged.sizeBytes());
                    try (OutputStream out = event.getOutputStream()) {
                        vaultService.writeStagedFile(staged.stagingKey(), staged.fileKey(), out);
                    }
                };
            }

            @Override
            public DownloadHandler download(Long fieldId, boolean inline) {
                return event -> {
                    FileInfo info = vaultService.fileInfo(currentUser.require(), existing.id(), fieldId);
                    if (inline) {
                        event.inline(info.fileName());
                    } else {
                        event.setFileName(info.fileName());
                    }
                    if (inline) {
                        event.setContentType(previewContentType(info.contentType()));
                    } else if (info.contentType() != null) {
                        event.setContentType(info.contentType());
                    }
                    event.setContentLength(info.sizeBytes());
                    try (OutputStream out = event.getOutputStream()) {
                        vaultService.writeFile(currentUser.require(), currentUser.masterKey(),
                                existing.id(), fieldId, out);
                    }
                };
            }
        };
    }

    private static String previewContentType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        return contentType.startsWith("text/") || "application/csv".equals(contentType)
                ? "text/plain; charset=utf-8"
                : contentType;
    }

    @Override
    public String getPageTitle() {
        return Translations.of("entries.pageTitle") + " | " + Translations.of("app.name");
    }
}
