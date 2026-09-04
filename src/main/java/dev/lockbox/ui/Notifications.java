package dev.lockbox.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

final class Notifications {

    private static final int DURATION_MS = 3500;

    private Notifications() {
    }

    static void success(String message) {
        Icon icon = new Icon(VaadinIcon.CHECK_CIRCLE);
        icon.getStyle().set("width", "18px").set("height", "18px").set("color", "var(--aura-green)");

        HorizontalLayout content = new HorizontalLayout(icon, new Span(message));
        content.setAlignItems(FlexComponent.Alignment.CENTER);
        content.setPadding(false);

        Notification notification = new Notification(content);
        notification.addThemeVariants(NotificationVariant.SUCCESS);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(DURATION_MS);
        notification.open();
    }
}
