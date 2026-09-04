package dev.lockbox.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
public class ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorage.class);
    private static final String ENCRYPTED_CONTENT_TYPE = "application/octet-stream";

    private final S3Client client;
    private final StorageProperties properties;

    public ObjectStorage(S3Client client, StorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucket() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (NoSuchBucketException e) {
            log.info("Creating storage bucket {}", properties.bucket());
            client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
        } catch (S3Exception e) {
            log.warn("Cannot verify the storage bucket {}: {}", properties.bucket(), e.getMessage());
        }
    }

    public void put(String key, byte[] content) {
        try {
            client.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(key)
                            .contentType(ENCRYPTED_CONTENT_TYPE)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception e) {
            throw new StorageException("Cannot store object " + key, e);
        }
    }

    public byte[] get(String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (S3Exception e) {
            throw new StorageException("Cannot read object " + key, e);
        }
    }

    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception e) {
            throw new StorageException("Cannot delete object " + key, e);
        }
    }
}
