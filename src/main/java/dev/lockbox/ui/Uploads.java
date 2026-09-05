package dev.lockbox.ui;

import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;

final class Uploads {

    private Uploads() {
    }

    static void translate(Upload upload, String chooseKey, String dropKey, String tooBigMessage) {
        upload.setI18n(new UploadI18N()
                .setAddFiles(new UploadI18N.AddFiles()
                        .setOne(Translations.of(chooseKey))
                        .setMany(Translations.of(chooseKey)))
                .setDropFiles(new UploadI18N.DropFiles()
                        .setOne(Translations.of(dropKey))
                        .setMany(Translations.of(dropKey)))
                .setError(new UploadI18N.Error()
                        .setFileIsTooBig(tooBigMessage)
                        .setIncorrectFileType(Translations.of("upload.wrongType"))
                        .setTooManyFiles(Translations.of("upload.tooMany"))));
    }
}
