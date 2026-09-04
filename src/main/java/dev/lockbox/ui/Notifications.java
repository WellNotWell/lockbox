package dev.lockbox.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

final class Notifications {

    private static final int DURATION_MS = 3500;

    private Notifications() {
    }

    static void success(String message) {
        Icon icon = new Icon(VaadinIcon.CHECK_CIRCLE);
        icon.getStyle().set("width", "18px").set("height", "18px").set("color", "light-dark(#1e8e3e, #5cd68a)");

        Span text = new Span(message);
        text.getStyle().set("color", "light-dark(#0f5132, #c8f0d5)");

        Div content = new Div(icon, text);
        content.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "10px")
                .set("background", "light-dark(#e8f5ea, #14331f)")
                .set("border", "1px solid light-dark(#b7dfc2, #2f6b45)")
                .set("border-radius", "10px")
                .set("padding", "12px 18px")
                .set("font-weight", "500")
                .set("box-shadow", "0 6px 20px light-dark(rgba(15, 81, 50, 0.12), rgba(0, 0, 0, 0.4))");

        Notification notification = new Notification(content);
        notification.addThemeVariants(NotificationVariant.SUCCESS);
        notification.setPosition(Notification.Position.TOP_CENTER);
        notification.setDuration(DURATION_MS);
        notification.open();
    }
}
