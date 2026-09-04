package dev.lockbox.crypto;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

@Component
public class KeyDerivation {

    private static final String KEY_TYPE = "AES";
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final CryptoProperties properties;

    public KeyDerivation(CryptoProperties properties) {
        this.properties = properties;
    }

    public byte[] newSalt() {
        byte[] salt = new byte[properties.saltLength()];
        random.nextBytes(salt);
        return salt;
    }

    public SecretKey deriveMasterKey(char[] password, byte[] salt) {
        if (password == null || password.length == 0) {
            throw new CryptoException("Master password must not be empty");
        }
        if (salt == null || salt.length == 0) {
            throw new CryptoException("Key salt must not be empty");
        }

        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(properties.argon2MemoryKb())
                .withIterations(properties.argon2Iterations())
                .withParallelism(properties.argon2Parallelism())
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);

        byte[] keyBytes = new byte[KEY_LENGTH_BYTES];
        try {
            generator.generateBytes(password, keyBytes);
            return new SecretKeySpec(keyBytes, KEY_TYPE);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }
}
