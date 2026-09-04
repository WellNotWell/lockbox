package dev.lockbox.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyDerivationTest {

    private final KeyDerivation keyDerivation = new KeyDerivation(new CryptoProperties(65536, 3, 1, 16));

    @Test
    @DisplayName("The same password and salt always give the same key")
    void isDeterministic() {
        byte[] salt = keyDerivation.newSalt();

        SecretKey first = keyDerivation.deriveMasterKey("correct horse".toCharArray(), salt);
        SecretKey second = keyDerivation.deriveMasterKey("correct horse".toCharArray(), salt);

        assertThat(first.getEncoded()).isEqualTo(second.getEncoded());
    }

    @Test
    @DisplayName("A different password gives a different key")
    void dependsOnPassword() {
        byte[] salt = keyDerivation.newSalt();

        SecretKey key = keyDerivation.deriveMasterKey("correct horse".toCharArray(), salt);
        SecretKey other = keyDerivation.deriveMasterKey("wrong horse".toCharArray(), salt);

        assertThat(key.getEncoded()).isNotEqualTo(other.getEncoded());
    }

    @Test
    @DisplayName("The same password with a different salt gives a different key")
    void dependsOnSalt() {
        SecretKey key = keyDerivation.deriveMasterKey("correct horse".toCharArray(), keyDerivation.newSalt());
        SecretKey other = keyDerivation.deriveMasterKey("correct horse".toCharArray(), keyDerivation.newSalt());

        assertThat(key.getEncoded()).isNotEqualTo(other.getEncoded());
    }

    @Test
    @DisplayName("Derived key is a 256 bit AES key")
    void producesAes256Key() {
        SecretKey key = keyDerivation.deriveMasterKey("correct horse".toCharArray(), keyDerivation.newSalt());

        assertThat(key.getAlgorithm()).isEqualTo("AES");
        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    @DisplayName("Every salt is random and of the configured length")
    void generatesRandomSalts() {
        byte[] first = keyDerivation.newSalt();
        byte[] second = keyDerivation.newSalt();

        assertThat(first).hasSize(16).isNotEqualTo(second);
    }

    @Test
    @DisplayName("Empty password or salt is rejected")
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> keyDerivation.deriveMasterKey(new char[0], keyDerivation.newSalt()))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("Master password");
        assertThatThrownBy(() -> keyDerivation.deriveMasterKey("password".toCharArray(), new byte[0]))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("Key salt");
    }
}
