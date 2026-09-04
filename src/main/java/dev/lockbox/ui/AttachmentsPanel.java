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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import dev.lockbox.vault.Attachment;
import dev.lockbox.vault.StoredFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class AttachmentsPanel extends VerticalLayout {

    private final Supplier<List<Attachment>> loader;
    private final Function<Attachment, StoredFile> opener;
    private final Consumer<Attachment> remover;

    private final VerticalLayout list = new VerticalLayout();
    private final Span empty = new Span(Translations.of("attachments.empty"));

    AttachmentsPanel(long maxFileSize,
                     Supplier<List<Attachment>> loader,
                     Consumer<StoredFile> uploader,
                     Function<Attachment, StoredFile> opener,
                     Consumer<Attachment> remover) {
        this.loader = loader;
        this.opener = opener;
        this.remover = remover;

        setPadding(false);
        setSpacing(false);
        setWidthFull();

        Span header = new Span(Translations.of("attachments.header"));
        header.getStyle().set("font-weight", "600").set("margin-bottom", "6px");

        empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        list.setPadding(false);
        list.setSpacing(false);
        list.setWidthFull();

        add(header, empty, list, uploadComponent(maxFileSize, uploader));
        refresh();
    }

    private Upload uploadComponent(long maxFileSize, Consumer<StoredFile> uploader) {
        MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setMaxFileSize((int) maxFileSize);
        upload.setWidthFull();
        upload.setI18n(uploadTranslations(maxFileSize));
        upload.addSucceededListener(event -> {
            byte[] content = buffer.getOutputBuffer(event.getFileName()).toByteArray();
            uploader.accept(new StoredFile(event.getFileName(), event.getMIMEType(), content));
            upload.clearFileList();
            refresh();
            Notifications.success(Translations.of("attachments.uploaded"));
        });
        return upload;
    }

    private UploadI18N uploadTranslations(long maxFileSize) {
        return new UploadI18N()
                .setAddFiles(new UploadI18N.AddFiles()
                        .setOne(Translations.of("attachments.upload.add"))
                        .setMany(Translations.of("attachments.upload.add")))
                .setDropFiles(new UploadI18N.DropFiles()
                        .setOne(Translations.of("attachments.upload.drop"))
                        .setMany(Translations.of("attachments.upload.drop")))
                .setError(new UploadI18N.Error()
                        .setFileIsTooBig(Translations.of("attachments.upload.tooBig", readableSize(maxFileSize)))
                        .setIncorrectFileType(Translations.of("attachments.upload.wrongType"))
                        .setTooManyFiles(Translations.of("attachments.upload.tooMany")));
    }

    private void refresh() {
        list.removeAll();
        List<Attachment> attachments = loader.get();
        attachments.forEach(attachment -> list.add(row(attachment)));
        empty.setVisible(attachments.isEmpty());
    }

    private HorizontalLayout row(Attachment attachment) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing(true);
        row.setAlignItems(Alignment.CENTER);
        row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-10pct)").set("padding", "6px 0");

        Icon icon = new Icon(isImage(attachment) ? VaadinIcon.PICTURE : VaadinIcon.FILE_O);
        icon.getStyle().set("width", "16px").set("height", "16px")
                .set("color", "var(--lumo-secondary-text-color)");

        Anchor download = new Anchor(downloadHandler(attachment, false), attachment.getFileName());
        download.getElement().setAttribute("download", true);

        Span size = new Span(readableSize(attachment.getSizeBytes()));
        size.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)").set("white-space", "nowrap");

        row.add(icon, download, size);
        row.expand(download);

        if (isImage(attachment)) {
            Button preview = new Button(new Icon(VaadinIcon.EYE), event -> preview(attachment));
            preview.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            preview.getElement().setAttribute("title", Translations.of("attachments.preview"));
            row.add(preview);
        }

        Button delete = new Button(new Icon(VaadinIcon.TRASH), event -> Confirmations.ask(
                "confirm.deleteAttachment.header",
                Translations.of("confirm.deleteAttachment.text", attachment.getFileName()),
                "common.delete",
                true,
                () -> {
                    remover.accept(attachment);
                    refresh();
                }));
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        delete.getElement().setAttribute("title", Translations.of("common.delete"));
        row.add(delete);

        return row;
    }

    private void preview(Attachment attachment) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(attachment.getFileName());
        dialog.setWidth("640px");

        Image image = new Image(downloadHandler(attachment, true), attachment.getFileName());
        image.setWidthFull();
        dialog.add(image);

        Button close = new Button(Translations.of("common.close"), event -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        dialog.getFooter().add(close);
        dialog.open();
    }

    private DownloadHandler downloadHandler(Attachment attachment, boolean inline) {
        var handler = DownloadHandler.fromInputStream(event -> {
            StoredFile file = opener.apply(attachment);
            InputStream content = new ByteArrayInputStream(file.content());
            return new DownloadResponse(content, file.fileName(), file.contentType(), file.content().length);
        });
        return inline ? handler.inline() : handler;
    }

    private boolean isImage(Attachment attachment) {
        return attachment.getContentType() != null && attachment.getContentType().startsWith("image/");
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return Translations.of("attachments.size.b", bytes);
        }
        if (bytes < 1024 * 1024) {
            return Translations.of("attachments.size.kb", Math.round(bytes / 1024.0));
        }
        return Translations.of("attachments.size.mb", bytes / (1024.0 * 1024.0));
    }
}
