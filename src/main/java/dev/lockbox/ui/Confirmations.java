package dev.lockbox.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;

final class Confirmations {

    private Confirmations() {
    }

    static void ask(String headerKey, String text, String confirmKey, boolean destructive, Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(Translations.of(headerKey));
        dialog.setWidth("440px");
        dialog.setCloseOnOutsideClick(true);

        Paragraph message = new Paragraph(text);
        message.getStyle().set("margin", "0");
        dialog.add(message);

        Button cancel = new Button(Translations.of("common.cancel"), event -> dialog.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button confirm = new Button(Translations.of(confirmKey), event -> {
            dialog.close();
            onConfirm.run();
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (destructive) {
            confirm.addThemeVariants(ButtonVariant.LUMO_ERROR);
        }

        dialog.getFooter().add(cancel, confirm);
        dialog.open();
    }
}
