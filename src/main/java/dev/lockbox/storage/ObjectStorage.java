package dev.lockbox.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;

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
        } catch (SdkException e) {
            log.warn("Storage is not reachable at {}, file fields will fail until it is: {}",
                    properties.endpoint(), e.getMessage());
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
        } catch (SdkException e) {
            throw new StorageException("Cannot store object " + key, e);
        }
    }

    public InputStream openForReading(String key) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket()).key(key).build());
        } catch (SdkException e) {
            throw new StorageException("Cannot read object " + key, e);
        }
    }

    public MultipartUploadStream openForWriting(String key) {
        try {
            return new MultipartUploadStream(client, properties.bucket(), key, ENCRYPTED_CONTENT_TYPE);
        } catch (SdkException e) {
            throw new StorageException("Cannot start upload of object " + key, e);
        }
    }

    public void readInto(String key, OutputStream target) {
        try (InputStream source = client.getObject(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build())) {
            source.transferTo(target);
        } catch (SdkException | IOException e) {
            throw new StorageException("Cannot read object " + key, e);
        }
    }

    public void copy(String sourceKey, String targetKey) {
        try {
            client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(properties.bucket()).sourceKey(sourceKey)
                    .destinationBucket(properties.bucket()).destinationKey(targetKey)
                    .build());
        } catch (SdkException e) {
            throw new StorageException("Cannot copy object " + sourceKey, e);
        }
    }

    public List<String> keysOlderThan(String prefix, Instant cutoff) {
        try {
            return client.listObjectsV2(ListObjectsV2Request.builder()
                            .bucket(properties.bucket()).prefix(prefix).build())
                    .contents().stream()
                    .filter(object -> object.lastModified().isBefore(cutoff))
                    .map(S3Object::key)
                    .toList();
        } catch (SdkException e) {
            throw new StorageException("Cannot list objects under " + prefix, e);
        }
    }

    public byte[] get(String key) {
        try {
            return client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build()).asByteArray();
        } catch (SdkException e) {
            throw new StorageException("Cannot read object " + key, e);
        }
    }

    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            throw new StorageException("Cannot delete object " + key, e);
        }
    }
}
