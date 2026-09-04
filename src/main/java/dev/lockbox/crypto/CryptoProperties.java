package dev.lockbox.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lockbox.crypto")
public record CryptoProperties(
        @DefaultValue("65536") int argon2MemoryKb,
        @DefaultValue("3") int argon2Iterations,
        @DefaultValue("1") int argon2Parallelism,
        @DefaultValue("16") int saltLength
) {
}
