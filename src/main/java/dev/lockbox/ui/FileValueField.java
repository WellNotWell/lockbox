package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.FileInfo;
import dev.lockbox.vault.StoredFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.function.Function;

class FileValueField extends HorizontalLayout {

    private static final String MASK = "••••••••••";

    private final long maxFileSize;
    private final Function<Long, StoredFile> opener;

    private final Upload upload;
    private final HorizontalLayout chosen = new HorizontalLayout();
    private final Span name = new Span();
    private final Span size = new Span();

    private Long fieldId;
    private FileInfo info;
    private StoredFile uploaded;
    private boolean secret;
    private boolean revealed;

    FileValueField(long maxFileSize, Function<Long, StoredFile> opener) {
        this.maxFileSize = maxFileSize;
        this.opener = opener;

        setSpacing(false);
        setPadding(false);
        setWidthFull();
        setAlignItems(Alignment.CENTER);

        upload = newUpload();
        chosen.setSpacing(true);
        chosen.setPadding(false);
        chosen.setAlignItems(Alignment.CENTER);
        chosen.setVisible(false);
        chosen.getStyle().set("padding-inline-start", "var(--vaadin-gap-s)");

        size.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)").set("white-space", "nowrap");

        chosen.add(name, size);
        add(upload, chosen);
        expand(upload);
    }

    void setExisting(DecryptedField field) {
        fieldId = field.id();
        info = field.file();
        upload.setVisible(false);
        chosen.setVisible(true);
        render();
    }

    void setSecret(boolean isSecret) {
        secret = isSecret;
        revealed = false;
        render();
    }

    boolean hasFile() {
        return uploaded != null || fieldId != null;
    }

    StoredFile uploadedFile() {
        return uploaded;
    }

    Long keptId() {
        return uploaded == null ? fieldId : null;
    }

    private Upload newUpload() {
        MemoryBuffer buffer = new MemoryBuffer();
        Upload component = new Upload(buffer);
        component.setMaxFiles(1);
        component.setMaxFileSize((int) maxFileSize);
        component.setWidthFull();
        component.setI18n(uploadTranslations());
        component.addSucceededListener(event -> {
            uploaded = new StoredFile(event.getFileName(), event.getMIMEType(),
                    ((ByteArrayOutputStream) buffer.getFileData().getOutputBuffer()).toByteArray());
            component.clearFileList();
            component.setVisible(false);
            chosen.setVisible(true);
            render();
        });
        return component;
    }

    private UploadI18N uploadTranslations() {
        return new UploadI18N()
                .setAddFiles(new UploadI18N.AddFiles()
                        .setOne(Translations.of("entry.file.choose"))
                        .setMany(Translations.of("entry.file.choose")))
                .setDropFiles(new UploadI18N.DropFiles()
                        .setOne(Translations.of("entry.file.drop"))
                        .setMany(Translations.of("entry.file.drop")))
                .setError(new UploadI18N.Error()
                        .setFileIsTooBig(Translations.of("entry.file.tooBig", Sizes.readable(maxFileSize)))
                        .setIncorrectFileType(Translations.of("entry.file.wrongType"))
                        .setTooManyFiles(Translations.of("entry.file.tooMany")));
    }

    private void render() {
        if (!hasFile()) {
            return;
        }
        chosen.removeAll();
        if (secret && !revealed) {
            Span masked = new Span(MASK);
            masked.getStyle().set("letter-spacing", "2px").set("cursor", "pointer");
            masked.getElement().setAttribute("title", Translations.of("entry.file.reveal"));
            masked.getElement().addEventListener("click", event -> {
                revealed = true;
                render();
            });
            chosen.add(masked);
            return;
        }

        chosen.add(nameComponent());
        size.setText(Sizes.readable(currentSize()));
        chosen.add(size);
        if (isImage()) {
            Button preview = new Button(new Icon(VaadinIcon.PICTURE), event -> preview());
            preview.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            preview.setTooltipText(Translations.of("entry.file.preview"));
            chosen.add(preview);
        }
    }

    private com.vaadin.flow.component.Component nameComponent() {
        if (uploaded != null) {
            name.setText(uploaded.fileName());
            return name;
        }
        Anchor download = new Anchor(downloadHandler(false), info.fileName());
        download.getElement().setAttribute("download", true);
        return download;
    }

    private void preview() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(currentName());
        dialog.setWidth("640px");

        Image image = uploaded != null
                ? new Image(uploaded.content(), uploaded.fileName())
                : new Image(downloadHandler(true), info.fileName());
        image.setWidthFull();
        dialog.add(image);

        Button close = new Button(Translations.of("common.close"), event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private DownloadHandler downloadHandler(boolean inline) {
        var handler = DownloadHandler.fromInputStream(event -> {
            StoredFile file = opener.apply(fieldId);
            return new DownloadResponse(new ByteArrayInputStream(file.content()), file.fileName(),
                    file.contentType(), file.content().length);
        });
        return inline ? handler.inline() : handler;
    }

    private String currentName() {
        return uploaded != null ? uploaded.fileName() : info.fileName();
    }

    private long currentSize() {
        return uploaded != null ? uploaded.content().length : info.sizeBytes();
    }

    private boolean isImage() {
        String type = uploaded != null ? uploaded.contentType() : info.contentType();
        return type != null && type.startsWith("image/");
    }
}
