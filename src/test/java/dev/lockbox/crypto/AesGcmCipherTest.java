package dev.lockbox.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {

    private static final byte[] SECRET = "my database password".getBytes(StandardCharsets.UTF_8);

    private final AesGcmCipher cipher = new AesGcmCipher();
    private final SecretKey key = randomKey();
    private final SecretKey otherKey = randomKey();

    @Test
    @DisplayName("Encrypted data is restored exactly")
    void roundTrip() {
        byte[] payload = cipher.encrypt(SECRET, key);

        assertThat(cipher.decrypt(payload, key)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("The same text encrypted twice gives different payloads")
    void producesDifferentPayloadsForTheSameText() {
        byte[] first = cipher.encrypt(SECRET, key);
        byte[] second = cipher.encrypt(SECRET, key);

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first, key)).isEqualTo(cipher.decrypt(second, key));
    }

    @Test
    @DisplayName("Ciphertext never contains the plain text")
    void doesNotLeakPlainText() {
        byte[] payload = cipher.encrypt(SECRET, key);

        assertThat(new String(payload, StandardCharsets.UTF_8)).doesNotContain("password");
    }

    @Test
    @DisplayName("A single flipped byte breaks decryption")
    void detectsTampering() {
        byte[] payload = cipher.encrypt(SECRET, key);
        payload[payload.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(payload, key))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("wrong key or corrupted payload");
    }

    @Test
    @DisplayName("A changed initialization vector breaks decryption too")
    void detectsIvTampering() {
        byte[] payload = cipher.encrypt(SECRET, key);
        payload[0] ^= 0x01;

        assertThatThrownBy(() -> cipher.decrypt(payload, key)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Another key cannot read the data")
    void rejectsWrongKey() {
        byte[] payload = cipher.encrypt(SECRET, key);

        assertThatThrownBy(() -> cipher.decrypt(payload, otherKey)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Payload carries the initialization vector and the authentication tag")
    void payloadLayout() {
        byte[] payload = cipher.encrypt(SECRET, key);

        assertThat(payload).hasSize(AesGcmCipher.IV_LENGTH + SECRET.length + AesGcmCipher.TAG_LENGTH_BITS / 8);
        assertThat(Arrays.copyOfRange(payload, 0, AesGcmCipher.IV_LENGTH)).isNotEqualTo(new byte[AesGcmCipher.IV_LENGTH]);
    }

    @Test
    @DisplayName("Empty value is encrypted as well")
    void encryptsEmptyValue() {
        byte[] payload = cipher.encrypt(new byte[0], key);

        assertThat(cipher.decrypt(payload, key)).isEmpty();
    }

    @Test
    @DisplayName("Truncated payload is rejected with a clear message")
    void rejectsTruncatedPayload() {
        assertThatThrownBy(() -> cipher.decrypt(new byte[]{1, 2, 3}, key))
                .isInstanceOf(DecryptionException.class)
                .hasMessageContaining("too short");
    }

    private static SecretKey randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new SecretKeySpec(key, "AES");
    }
}
