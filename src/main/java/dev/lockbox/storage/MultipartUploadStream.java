package dev.lockbox.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MultipartUploadStream extends OutputStream {

    static final int PART_SIZE = 5 * 1024 * 1024;

    private final S3Client client;
    private final String bucket;
    private final String key;
    private final String uploadId;
    private final List<CompletedPart> parts = new ArrayList<>();
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(PART_SIZE);

    private int partNumber = 1;
    private boolean closed;

    MultipartUploadStream(S3Client client, String bucket, String key, String contentType) {
        this.client = client;
        this.bucket = bucket;
        this.key = key;
        this.uploadId = client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build()).uploadId();
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] data, int offset, int length) throws IOException {
        pending.write(data, offset, length);
        while (pending.size() >= PART_SIZE) {
            flushPart(PART_SIZE);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            flushPart(pending.size());
            client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                    .build());
        } catch (RuntimeException e) {
            abort();
            throw new StorageException("Cannot finish upload of object " + key, e);
        }
    }

    public void abort() {
        closed = true;
        try {
            client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId).build());
        } catch (RuntimeException ignored) {
            closed = true;
        }
    }

    private void flushPart(int size) {
        byte[] buffered = pending.toByteArray();
        byte[] part = size == buffered.length ? buffered : java.util.Arrays.copyOf(buffered, size);

        UploadPartRequest request = UploadPartRequest.builder()
                .bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber).build();
        String etag = client.uploadPart(request, RequestBody.fromBytes(part)).eTag();
        parts.add(CompletedPart.builder().partNumber(partNumber).eTag(etag).build());
        partNumber++;

        pending.reset();
        if (size < buffered.length) {
            pending.write(buffered, size, buffered.length - size);
        }
    }
}
