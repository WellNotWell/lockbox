package dev.lockbox.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class AesGcmCipher {

    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecureRandom random = new SecureRandom();

    public byte[] encrypt(byte[] plaintext, SecretKey key) {
        if (plaintext == null) {
            throw new CryptoException("Nothing to encrypt");
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Cannot encrypt data", e);
        }
    }

    public byte[] decrypt(byte[] payload, SecretKey key) {
        if (payload == null || payload.length <= IV_LENGTH) {
            throw new DecryptionException("Encrypted payload is too short to contain an initialization vector");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new DecryptionException("Cannot decrypt data: wrong key or corrupted payload", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Cannot decrypt data", e);
        }
    }
}
