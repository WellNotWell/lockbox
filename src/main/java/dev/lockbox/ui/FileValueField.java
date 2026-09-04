package dev.lockbox.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.component.upload.UploadI18N;
import dev.lockbox.vault.DecryptedField;
import dev.lockbox.vault.FileInfo;
import dev.lockbox.vault.StagedFile;
import dev.lockbox.vault.VaultService;

import javax.crypto.SecretKey;

class FileValueField extends HorizontalLayout {

    private static final String MASK = "••••••••••";

    private final long maxFileSize;
    private final FileAccess access;

    private final Upload upload;
    private final HorizontalLayout chosen = new HorizontalLayout();
    private final Span size = new Span();

    private Long fieldId;
    private FileInfo info;
    private StagedFile staged;
    private VaultService.StagingSession activeUpload;
    private SecretKey fileKey;
    private String stagingKey;
    private boolean secret;
    private boolean revealed;

    FileValueField(long maxFileSize, FileAccess access) {
        this.maxFileSize = maxFileSize;
        this.access = access;

        setSpacing(false);
        setPadding(false);
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        getStyle().set("min-width", "0");

        upload = newUpload();
        chosen.setSpacing(true);
        chosen.setPadding(false);
        chosen.setAlignItems(Alignment.CENTER);
        chosen.setVisible(false);
        chosen.setWidthFull();
        chosen.getStyle().set("padding-inline-start", "var(--vaadin-gap-s)")
                .set("min-width", "0").set("overflow", "hidden");

        size.getStyle().set("color", "var(--vaadin-text-color-secondary)")
                .set("font-size", "var(--aura-font-size-s)").set("white-space", "nowrap")
                .set("flex", "0 0 auto");

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
        return staged != null || fieldId != null;
    }

    StagedFile stagedFile() {
        return staged;
    }

    Long keptId() {
        return staged == null ? fieldId : null;
    }

    private Upload newUpload() {
        Upload component = new Upload();
        component.setMaxFiles(1);
        component.setMaxFileSize((int) maxFileSize);
        component.setWidthFull();
        component.setI18n(uploadTranslations());
        component.setReceiver((fileName, mimeType) -> {
            stagingKey = access.newStagingKey();
            activeUpload = access.openStaging(stagingKey);
            fileKey = activeUpload.fileKey();
            return activeUpload.upload().stream();
        });
        component.addSucceededListener(event -> {
            staged = new StagedFile(stagingKey, event.getFileName(), event.getMIMEType(),
                    event.getContentLength(), fileKey);
            activeUpload = null;
            component.clearFileList();
            component.setVisible(false);
            chosen.setVisible(true);
            render();
        });
        component.addFailedListener(event -> discardUpload());
        component.addFileRejectedListener(event -> discardUpload());
        return component;
    }

    private void discardUpload() {
        if (activeUpload != null) {
            activeUpload.upload().abort();
            activeUpload = null;
        }
        stagingKey = null;
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

        Component fileName = nameComponent();
        chosen.add(fileName);
        size.setText(Sizes.readable(currentSize()));
        chosen.add(size);
        if (isPreviewable()) {
            Button preview = new Button(new Icon(previewIcon()), event -> preview());
            preview.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            preview.setTooltipText(Translations.of("entry.file.preview"));
            preview.getStyle().set("flex", "0 0 auto");
            chosen.add(preview);
        }
        chosen.expand(fileName);
    }

    private void shorten(Component component, String fullName) {
        component.getElement().getStyle()
                .set("overflow", "hidden").set("text-overflow", "ellipsis")
                .set("white-space", "nowrap").set("min-width", "0").set("display", "block");
        component.getElement().setAttribute("title", fullName);
    }

    private Component nameComponent() {
        if (staged != null) {
            Span plain = new Span(staged.fileName());
            shorten(plain, staged.fileName());
            return plain;
        }
        Anchor download = new Anchor(access.download(fieldId, false), info.fileName());
        download.getElement().setAttribute("download", true);
        shorten(download, info.fileName());
        return download;
    }

    private VaadinIcon previewIcon() {
        if (isImage()) {
            return VaadinIcon.PICTURE;
        }
        return isPdf() ? VaadinIcon.FILE_TEXT : VaadinIcon.LINES;
    }

    private void preview() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(currentName());
        dialog.setWidth(isImage() ? "640px" : "860px");

        DownloadHandler handler = staged != null
                ? access.previewStaged(staged)
                : access.download(fieldId, true);

        if (isImage()) {
            Image image = new Image(handler, currentName());
            image.setWidthFull();
            dialog.add(image);
        } else {
            IFrame frame = new IFrame(handler);
            frame.setWidthFull();
            frame.setHeight("70vh");
            frame.getStyle().set("border", "0")
                    .set("background", "light-dark(#ffffff, #1c1c1f)");
            dialog.add(frame);
        }

        Button close = new Button(Translations.of("common.close"), event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private String currentName() {
        return staged != null ? staged.fileName() : info.fileName();
    }

    private boolean isPreviewable() {
        return isImage() || isPdf() || isPlainText();
    }

    private boolean isPdf() {
        return "application/pdf".equals(contentType());
    }

    private boolean isPlainText() {
        String type = contentType();
        return type != null && (type.startsWith("text/") || "application/csv".equals(type));
    }

    private String contentType() {
        return staged != null ? staged.contentType() : info.contentType();
    }

    private long currentSize() {
        return staged != null ? staged.sizeBytes() : info.sizeBytes();
    }

    private boolean isImage() {
        String type = contentType();
        return type != null && type.startsWith("image/");
    }
}
