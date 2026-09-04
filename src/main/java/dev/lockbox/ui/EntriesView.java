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
import dev.lockbox.vault.DecryptedEntry;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.StoredFile;
import dev.lockbox.vault.VaultService;
import jakarta.annotation.security.PermitAll;

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
                fieldId -> openFile(existing, fieldId),
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

    private StoredFile openFile(DecryptedEntry existing, Long fieldId) {
        return vaultService.openFile(currentUser.require(), currentUser.masterKey(), existing.id(), fieldId);
    }

    @Override
    public String getPageTitle() {
        return Translations.of("entries.pageTitle") + " | " + Translations.of("app.name");
    }
}
