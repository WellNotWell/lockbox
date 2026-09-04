package dev.lockbox.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("lockbox") String accessKey,
        @DefaultValue("lockbox-secret") String secretKey,
        @DefaultValue("lockbox") String bucket,
        @DefaultValue("25MB") DataSize maxAttachmentSize
) {
}
