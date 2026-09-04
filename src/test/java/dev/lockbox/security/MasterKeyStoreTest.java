package dev.lockbox.security;

import dev.lockbox.crypto.CryptoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterKeyStoreTest {

    private final MasterKeyStore store = new MasterKeyStore();

    @Test
    @DisplayName("Vault starts locked and asking for the key fails with a clear message")
    void startsLocked() {
        assertThat(store.isUnlocked()).isFalse();

        assertThatThrownBy(store::requireMasterKey)
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("Vault is locked");
    }

    @Test
    @DisplayName("Key is available after unlocking and gone after locking")
    void unlocksAndLocks() {
        store.unlock(new SecretKeySpec(new byte[32], "AES"));
        assertThat(store.isUnlocked()).isTrue();
        assertThat(store.requireMasterKey()).isNotNull();

        store.lock();

        assertThat(store.isUnlocked()).isFalse();
    }
}
