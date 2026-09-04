package dev.lockbox.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeViewTest {

    @Test
    @DisplayName("Home view renders the application title")
    void rendersTitle() {
        HomeView view = new HomeView();

        Component first = view.getComponentAt(0);

        assertThat(first).isInstanceOf(H1.class);
        assertThat(((H1) first).getText()).isEqualTo("Lockbox");
        assertThat(view.getComponentCount()).isEqualTo(2);
    }
}
