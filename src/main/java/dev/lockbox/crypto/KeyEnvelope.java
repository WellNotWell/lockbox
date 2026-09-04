package dev.lockbox.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

@Component
public class KeyEnvelope {

    private static final String KEY_TYPE = "AES";
    private static final int DATA_KEY_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();
    private final AesGcmCipher cipher;

    public KeyEnvelope(AesGcmCipher cipher) {
        this.cipher = cipher;
    }

    public SecretKey newDataKey() {
        byte[] key = new byte[DATA_KEY_LENGTH];
        random.nextBytes(key);
        return new SecretKeySpec(key, KEY_TYPE);
    }

    public byte[] wrap(SecretKey dataKey, SecretKey wrappingKey) {
        return cipher.encrypt(dataKey.getEncoded(), wrappingKey);
    }

    public SecretKey unwrap(byte[] wrappedKey, SecretKey wrappingKey) {
        byte[] key = cipher.decrypt(wrappedKey, wrappingKey);
        if (key.length != DATA_KEY_LENGTH) {
            throw new DecryptionException("Unwrapped data key has an unexpected length");
        }
        return new SecretKeySpec(key, KEY_TYPE);
    }
}
