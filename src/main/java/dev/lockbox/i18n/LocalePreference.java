package dev.lockbox.i18n;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletResponse;
import com.vaadin.flow.server.VaadinSession;
import jakarta.servlet.http.Cookie;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public final class LocalePreference {

    private static final String COOKIE_NAME = "lockbox-locale";
    private static final String SESSION_ATTRIBUTE = "lockbox-locale";
    private static final int ONE_YEAR = 365 * 24 * 60 * 60;

    private LocalePreference() {
    }

    public static Locale readFrom(VaadinRequest request) {
        if (request == null || request.getCookies() == null) {
            return LockboxI18NProvider.ENGLISH;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .map(LockboxI18NProvider::supportedOrDefault)
                .orElse(LockboxI18NProvider.ENGLISH);
    }

    public static Locale of(VaadinSession session) {
        Object stored = session.getAttribute(SESSION_ATTRIBUTE);
        return stored instanceof String language
                ? LockboxI18NProvider.supportedOrDefault(language)
                : LockboxI18NProvider.ENGLISH;
    }

    public static void applyTo(VaadinSession session, Locale locale) {
        session.setAttribute(SESSION_ATTRIBUTE, locale.getLanguage());
        session.setLocale(locale);
    }

    public static void remember(Locale locale) {
        Optional.ofNullable(VaadinService.getCurrentResponse())
                .filter(VaadinServletResponse.class::isInstance)
                .map(VaadinServletResponse.class::cast)
                .ifPresent(response -> response.addCookie(newCookie(locale)));
    }

    private static Cookie newCookie(Locale locale) {
        Cookie cookie = new Cookie(COOKIE_NAME, locale.getLanguage());
        cookie.setPath("/");
        cookie.setMaxAge(ONE_YEAR);
        return cookie;
    }
}
