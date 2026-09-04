package dev.lockbox.user;

import dev.lockbox.crypto.KeyDerivation;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeyDerivation keyDerivation;

    public RegistrationService(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               KeyDerivation keyDerivation) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.keyDerivation = keyDerivation;
    }

    @Transactional
    public User register(String username, String masterPassword) {
        String normalized = username.trim();
        if (userRepository.existsByUsernameIgnoreCase(normalized)) {
            throw new UserAlreadyExistsException(normalized);
        }
        User user = new User();
        user.setUsername(normalized);
        user.setPasswordHash(passwordEncoder.encode(masterPassword));
        user.setKeySalt(keyDerivation.newSalt());
        return userRepository.save(user);
    }
}
