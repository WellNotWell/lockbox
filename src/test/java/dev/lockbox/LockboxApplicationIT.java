package dev.lockbox;

import com.vaadin.flow.i18n.I18NProvider;
import dev.lockbox.i18n.LockboxI18NProvider;
import dev.lockbox.user.RegistrationService;
import dev.lockbox.user.User;
import dev.lockbox.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class LockboxApplicationIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private I18NProvider i18NProvider;

    @Test
    @DisplayName("Own pages may be framed by the app itself, but not by anyone else")
    void allowsSameOriginFraming() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.headers().firstValue("X-Frame-Options").orElseThrow())
                .isEqualTo("SAMEORIGIN");
    }

    @Test
    @DisplayName("Anonymous visitor is sent to the sign in page")
    void redirectsAnonymousVisitorToLogin() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElseThrow()).contains("/login");
    }

    @Test
    @DisplayName("Sign in page is available without authentication")
    void servesLoginPage() throws Exception {
        HttpResponse<String> response = get("/login");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).containsIgnoringCase("<!doctype html>");
    }

    @Test
    @DisplayName("Health endpoint stays open so the container health check keeps working")
    void keepsHealthEndpointOpen() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    @Test
    @DisplayName("Registration stores a hashed password and a personal key salt")
    void storesRegisteredUser() {
        registrationService.register("integration-user", "correct horse battery");

        User stored = userRepository.findByUsernameIgnoreCase("integration-user").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("correct horse battery");
        assertThat(passwordEncoder.matches("correct horse battery", stored.getPasswordHash())).isTrue();
        assertThat(stored.getKeySalt()).hasSize(16);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("English stays English even when the server runs with another system language")
    void translatesWithTheRequestedLanguage() {
        assertThat(i18NProvider.getTranslation("common.signOut", LockboxI18NProvider.ENGLISH)).isEqualTo("Sign out");
        assertThat(i18NProvider.getTranslation("common.signOut", LockboxI18NProvider.RUSSIAN)).isEqualTo("Выйти");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
