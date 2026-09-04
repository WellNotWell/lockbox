package dev.lockbox.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

class AuthPanel extends VerticalLayout {

    static final String WIDTH = "360px";

    AuthPanel(String subtitleKey, Component... content) {
        setWidth(WIDTH);
        setPadding(false);
        setAlignItems(Alignment.STRETCH);

        H1 title = new H1(Translations.of("app.name"));
        title.getStyle().set("text-align", "center");

        Paragraph subtitle = new Paragraph(Translations.of(subtitleKey));
        subtitle.getStyle().set("color", "var(--vaadin-text-color-secondary)").set("text-align", "center");

        add(title, subtitle);
        add(content);
    }

    void addFooterLink(Component link) {
        link.getElement().getStyle().set("text-align", "center");
        add(link);
    }
}
