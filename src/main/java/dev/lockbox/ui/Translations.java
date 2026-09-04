package dev.lockbox.ui;

import com.vaadin.flow.component.UI;

final class Translations {

    private Translations() {
    }

    static String of(String key, Object... params) {
        UI ui = UI.getCurrent();
        return ui == null ? key : ui.getTranslation(key, params);
    }
}
