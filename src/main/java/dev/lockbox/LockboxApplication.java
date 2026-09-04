package dev.lockbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LockboxApplication {

    public static void main(String[] args) {
        SpringApplication.run(LockboxApplication.class, args);
    }
}
