package dev.lockbox.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyEnvelopeTest {

    private final AesGcmCipher cipher = new AesGcmCipher();
    private final KeyEnvelope envelope = new KeyEnvelope(cipher);
    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));

    @Test
    @DisplayName("Data key survives wrapping and unwrapping")
    void roundTrip() {
        SecretKey masterKey = masterKey("master password");
        SecretKey dataKey = envelope.newDataKey();

        byte[] wrapped = envelope.wrap(dataKey, masterKey);

        assertThat(envelope.unwrap(wrapped, masterKey).getEncoded()).isEqualTo(dataKey.getEncoded());
    }

    @Test
    @DisplayName("Every data key is different")
    void generatesDifferentDataKeys() {
        assertThat(envelope.newDataKey().getEncoded()).isNotEqualTo(envelope.newDataKey().getEncoded());
    }

    @Test
    @DisplayName("Wrapped key is useless without the master key")
    void rejectsWrongMasterKey() {
        byte[] wrapped = envelope.wrap(envelope.newDataKey(), masterKey("master password"));

        assertThatThrownBy(() -> envelope.unwrap(wrapped, masterKey("another password")))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Changing the master password does not require re-encrypting the data")
    void supportsMasterPasswordChange() {
        SecretKey oldMasterKey = masterKey("old password");
        SecretKey newMasterKey = masterKey("new password");
        SecretKey dataKey = envelope.newDataKey();
        byte[] secret = cipher.encrypt("screenshot bytes".getBytes(StandardCharsets.UTF_8), dataKey);
        byte[] wrapped = envelope.wrap(dataKey, oldMasterKey);

        byte[] rewrapped = envelope.wrap(envelope.unwrap(wrapped, oldMasterKey), newMasterKey);

        SecretKey recovered = envelope.unwrap(rewrapped, newMasterKey);
        assertThat(cipher.decrypt(secret, recovered)).isEqualTo("screenshot bytes".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> envelope.unwrap(rewrapped, oldMasterKey)).isInstanceOf(DecryptionException.class);
    }

    @Test
    @DisplayName("Full path: password to master key, master key to data key, data key to secret")
    void fullEnvelopePath() {
        SecretKey masterKey = masterKey("master password");
        SecretKey dataKey = envelope.newDataKey();
        byte[] storedKey = envelope.wrap(dataKey, masterKey);
        byte[] storedSecret = cipher.encrypt("api-token-42".getBytes(StandardCharsets.UTF_8), dataKey);

        SecretKey restoredKey = envelope.unwrap(storedKey, masterKey);
        String restoredSecret = new String(cipher.decrypt(storedSecret, restoredKey), StandardCharsets.UTF_8);

        assertThat(restoredSecret).isEqualTo("api-token-42");
    }

    private SecretKey masterKey(String password) {
        return keyDerivation.deriveMasterKey(password.toCharArray(), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
    }
}
