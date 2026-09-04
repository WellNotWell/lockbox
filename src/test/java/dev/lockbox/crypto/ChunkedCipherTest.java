package dev.lockbox.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkedCipherTest {

    private static final int CHUNK = ChunkedCipher.CHUNK_SIZE;

    private final ChunkedCipher cipher = new ChunkedCipher();
    private final SecretKey key = newKey();
    private final SecretKey otherKey = newKey();

    @ParameterizedTest(name = "{0} bytes survive the round trip")
    @ValueSource(ints = {0, 1, 1024, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK, 3 * CHUNK + 12345})
    @DisplayName("Any size comes back byte for byte")
    void roundTrip(int size) throws IOException {
        byte[] plain = content(size);

        byte[] encrypted = encrypt(plain);
        byte[] decrypted = decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("The predicted encrypted length matches what is actually written")
    void predictsEncryptedLength() throws IOException {
        for (int size : new int[]{0, 1, CHUNK - 1, CHUNK, CHUNK + 1, 2 * CHUNK + 7}) {
            assertThat(encrypt(content(size)).length)
                    .as("size %d", size)
                    .isEqualTo((int) cipher.encryptedLength(size));
        }
    }

    @Test
    @DisplayName("The ciphertext holds no readable trace of the content")
    void hidesContent() throws IOException {
        byte[] plain = "passport number 4815162342".repeat(1000).getBytes();

        byte[] encrypted = encrypt(plain);

        assertThat(new String(encrypted)).doesNotContain("passport");
    }

    @Test
    @DisplayName("A flipped byte anywhere is rejected")
    void rejectsTamperedByte() throws IOException {
        byte[] encrypted = encrypt(content(3 * CHUNK));
        encrypted[encrypted.length / 2] ^= 0x01;

        assertThatThrownBy(() -> decrypt(encrypted)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Swapping two chunks is rejected, because the index is authenticated")
    void rejectsReorderedChunks() throws IOException {
        byte[] plain = content(3 * CHUNK);
        byte[] encrypted = encrypt(plain);

        int frame = 1 + Integer.BYTES + CHUNK + 16;
        int header = encrypted.length - 4 * frame + frame;
        byte[] swapped = encrypted.clone();
        System.arraycopy(encrypted, header + frame, swapped, header, frame);
        System.arraycopy(encrypted, header, swapped, header + frame, frame);

        assertThatThrownBy(() -> decrypt(swapped)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Dropping a chunk is rejected")
    void rejectsDroppedChunk() throws IOException {
        byte[] plain = content(3 * CHUNK);
        byte[] encrypted = encrypt(plain);
        int frame = 1 + Integer.BYTES + CHUNK + 16;
        int headerLength = encrypted.length - 3 * frame - (1 + Integer.BYTES + 16);

        byte[] shortened = new byte[encrypted.length - frame];
        System.arraycopy(encrypted, 0, shortened, 0, headerLength);
        System.arraycopy(encrypted, headerLength + frame, shortened, headerLength,
                encrypted.length - headerLength - frame);

        assertThatThrownBy(() -> decrypt(shortened)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Cutting off the tail is rejected, the payload never says it ended")
    void rejectsTruncation() throws IOException {
        byte[] encrypted = encrypt(content(3 * CHUNK));
        byte[] truncated = Arrays.copyOf(encrypted, encrypted.length - (1 + Integer.BYTES + 16));

        assertThatThrownBy(() -> decrypt(truncated))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("without a final chunk");
    }

    @Test
    @DisplayName("Appending anything after the final chunk is rejected")
    void rejectsTrailingData() throws IOException {
        byte[] encrypted = encrypt(content(1024));
        byte[] extended = Arrays.copyOf(encrypted, encrypted.length + 1);

        assertThatThrownBy(() -> decrypt(extended))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("past its final chunk");
    }

    @Test
    @DisplayName("A payload without the header is rejected")
    void rejectsForeignPayload() {
        assertThatThrownBy(() -> decrypt("not a lockbox payload at all".getBytes()))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("expected header");
    }

    @Test
    @DisplayName("Another key cannot decrypt")
    void rejectsForeignKey() throws IOException {
        byte[] encrypted = encrypt(content(1024));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> cipher.decrypt(new ByteArrayInputStream(encrypted), out, otherKey))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Every chunk gets its own initialization vector, so equal chunks differ")
    void usesDistinctIvPerChunk() throws IOException {
        byte[] plain = new byte[2 * CHUNK];

        byte[] encrypted = encrypt(plain);

        int frame = 1 + Integer.BYTES + CHUNK + 16;
        int start = encrypted.length - 2 * frame - (1 + Integer.BYTES + 16);
        byte[] first = Arrays.copyOfRange(encrypted, start, start + frame);
        byte[] second = Arrays.copyOfRange(encrypted, start + frame, start + 2 * frame);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("A broken final chunk still lets the earlier verified chunks through")
    void deliversVerifiedChunksOnly() throws IOException {
        byte[] plain = content(3 * CHUNK);
        byte[] encrypted = encrypt(plain);
        encrypted[encrypted.length - 20] ^= 0x01;

        CountingStream counting = new CountingStream();
        assertThatThrownBy(() -> cipher.decrypt(new ByteArrayInputStream(encrypted), counting, key))
                .isInstanceOf(DecryptionException.class);

        assertThat(counting.written).isEqualTo(3L * CHUNK);
    }

    @Test
    @DisplayName("Encryption gives chunks away as it goes instead of holding the file")
    void streamsWhileEncrypting() throws IOException {
        int size = 40 * 1024 * 1024;
        ObservingStream observing = new ObservingStream();

        try (java.io.OutputStream encrypting = cipher.encryptingStream(observing, key)) {
            byte[] block = new byte[64 * 1024];
            for (int written = 0; written < size; written += block.length) {
                encrypting.write(block);
                if (written == 0) {
                    continue;
                }
                assertThat(observing.total)
                        .as("nothing was handed over after %d bytes went in", written)
                        .isPositive();
            }
        }

        assertThat(observing.largestWrite).isLessThanOrEqualTo(CHUNK + 64);
        assertThat(observing.total).isEqualTo(cipher.encryptedLength(size));
    }

    private byte[] encrypt(byte[] plain) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cipher.encrypt(new ByteArrayInputStream(plain), out, key);
        return out.toByteArray();
    }

    private byte[] decrypt(byte[] encrypted) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cipher.decrypt(new ByteArrayInputStream(encrypted), out, key);
        return out.toByteArray();
    }

    private static byte[] content(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    private static SecretKey newKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static class ObservingStream extends OutputStream {

        private long total;
        private int largestWrite;

        @Override
        public void write(int b) {
            total++;
            largestWrite = Math.max(largestWrite, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            total += len;
            largestWrite = Math.max(largestWrite, len);
        }
    }

    private static class CountingStream extends OutputStream {

        private long written;

        @Override
        public void write(int b) {
            written++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            written += len;
        }
    }
}
