package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.UploadHandler;
import dev.lockbox.backup.BackupService;
import dev.lockbox.crypto.DecryptionException;
import dev.lockbox.security.CurrentUser;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.time.LocalDate;

class BackupDialog extends Dialog {

    private final PasswordField exportPassword = new PasswordField(Translations.of("backup.newPassword"));
    private final PasswordField exportConfirmation = new PasswordField(Translations.of("backup.newPassword.repeat"));
    private final PasswordField restorePassword = new PasswordField(Translations.of("backup.archivePassword"));

    private byte[] uploaded;

    BackupDialog(BackupService backupService, CurrentUser currentUser, Runnable onRestored) {
        setHeaderTitle(Translations.of("backup.dialog"));
        setWidth("470px");

        exportPassword.setWidthFull();
        exportPassword.setHelperText(Translations.of("backup.newPassword.helper",
                RegistrationFormValidator.MIN_PASSWORD_LENGTH));
        exportConfirmation.setWidthFull();
        restorePassword.setWidthFull();
        restorePassword.setHelperText(Translations.of("backup.archivePassword.helper"));

        Anchor download = new Anchor(exportHandler(backupService, currentUser),
                Translations.of("backup.download"));
        download.getElement().setAttribute("download", true);
        download.setEnabled(false);

        Span hint = new Span(Translations.of("backup.error.exportPassword"));
        hint.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)");

        exportPassword.setValueChangeMode(ValueChangeMode.EAGER);
        exportConfirmation.setValueChangeMode(ValueChangeMode.EAGER);
        exportPassword.addValueChangeListener(event -> updateDownload(download, hint));
        exportConfirmation.addValueChangeListener(event -> updateDownload(download, hint));

        Upload upload = new Upload();
        upload.setMaxFiles(1);
        upload.setWidthFull();
        Uploads.translate(upload, "backup.upload.choose", "backup.upload.drop",
                Translations.of("upload.tooMany"));
        upload.setUploadHandler(UploadHandler.inMemory((metadata, data) -> uploaded = data));
        upload.addAllFinishedListener(event -> {
            if (uploaded == null) {
                return;
            }
            byte[] archive = uploaded;
            uploaded = null;
            restore(backupService, currentUser, archive, upload, onRestored);
        });

        VerticalLayout content = new VerticalLayout(
                section("backup.export", "backup.about"),
                exportPassword, exportConfirmation, download, hint,
                divider(),
                section("backup.restore", "backup.restore.about"),
                restorePassword, upload);
        content.setPadding(false);
        add(content);

        Button close = new Button(Translations.of("common.close"), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        getFooter().add(close);
    }

    private Hr divider() {
        Hr line = new Hr();
        line.getStyle().set("width", "100%").set("margin", "var(--vaadin-gap-s) 0")
                .set("border-color", "var(--vaadin-border-color-secondary)");
        return line;
    }

    private void updateDownload(Anchor download, Span hint) {
        String password = exportPassword.getValue();
        boolean ready = password != null
                && password.length() >= RegistrationFormValidator.MIN_PASSWORD_LENGTH
                && password.equals(exportConfirmation.getValue());
        download.setEnabled(ready);
        hint.setVisible(!ready);
    }

    private VerticalLayout section(String titleKey, String aboutKey) {
        Span title = new Span(Translations.of(titleKey));
        title.getStyle().set("font-weight", "600");

        Paragraph about = new Paragraph(Translations.of(aboutKey));
        about.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)").set("margin", "0");

        VerticalLayout layout = new VerticalLayout(title, about);
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }

    private DownloadHandler exportHandler(BackupService backupService, CurrentUser currentUser) {
        return event -> {
            event.setFileName("lockbox-%s.zip".formatted(LocalDate.now()));
            event.setContentType("application/zip");
            try (OutputStream out = event.getOutputStream()) {
                backupService.export(currentUser.require(), currentUser.masterKey(),
                        exportPassword.getValue(), out);
            }
        };
    }

    private void restore(BackupService backupService, CurrentUser currentUser, byte[] archive,
                         Upload upload, Runnable onRestored) {
        upload.clearFileList();
        restorePassword.setInvalid(false);
        if (restorePassword.isEmpty()) {
            markInvalid(restorePassword, "backup.error.passwordEmpty");
            return;
        }
        try {
            int restored = backupService.restore(currentUser.require(), currentUser.masterKey(),
                    restorePassword.getValue(), new ByteArrayInputStream(archive));
            close();
            onRestored.run();
            Notifications.success(Translations.of("backup.restored", restored));
        } catch (DecryptionException e) {
            markInvalid(restorePassword, "backup.error.wrongPassword");
        } catch (Exception e) {
            markInvalid(restorePassword, "backup.error.badArchive");
        }
    }

    private void markInvalid(PasswordField field, String messageKey) {
        field.setErrorMessage(Translations.of(messageKey));
        field.setInvalid(true);
    }
}
