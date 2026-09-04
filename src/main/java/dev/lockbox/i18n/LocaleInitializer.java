package dev.lockbox.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LocaleInitializer implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionEvent ->
                LocalePreference.applyTo(sessionEvent.getSession(),
                        LocalePreference.readFrom(sessionEvent.getRequest())));

        event.getSource().addUIInitListener(uiEvent -> {
            UI ui = uiEvent.getUI();
            ui.addBeforeEnterListener(enterEvent -> {
                Locale locale = preferred(ui.getSession());
                LocalePreference.applyTo(ui.getSession(), locale);
                ui.setLocale(locale);
            });
        });
    }

    private Locale preferred(VaadinSession session) {
        return session == null ? LockboxI18NProvider.ENGLISH : LocalePreference.of(session);
    }
}
