package dev.lockbox.security;

import dev.lockbox.crypto.CryptoException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import javax.crypto.SecretKey;
import java.io.Serializable;

@Component
@SessionScope
public class MasterKeyStore implements Serializable {

    private transient SecretKey masterKey;

    public void unlock(SecretKey masterKey) {
        this.masterKey = masterKey;
    }

    public void lock() {
        this.masterKey = null;
    }

    public boolean isUnlocked() {
        return masterKey != null;
    }

    public SecretKey requireMasterKey() {
        if (masterKey == null) {
            throw new CryptoException("Vault is locked, sign in again to unlock it");
        }
        return masterKey;
    }
}
