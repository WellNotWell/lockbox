package dev.lockbox;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("/styles/theme.css")
public class LockboxApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(LockboxApplication.class, args);
    }
}
