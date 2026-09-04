package dev.lockbox.security;

import dev.lockbox.user.User;
import dev.lockbox.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class CurrentUser {

    private final UserRepository userRepository;
    private final MasterKeyStore masterKeyStore;

    public CurrentUser(UserRepository userRepository, MasterKeyStore masterKeyStore) {
        this.userRepository = userRepository;
        this.masterKeyStore = masterKeyStore;
    }

    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in the current session");
        }
        return userRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user " + authentication.getName() + " is missing from the database"));
    }

    public SecretKey masterKey() {
        return masterKeyStore.requireMasterKey();
    }

    public String name() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }
}
