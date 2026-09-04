package dev.lockbox.security;

import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.user.User;
import dev.lockbox.user.UserRepository;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MasterKeyAuthenticationProvider implements AuthenticationProvider {

    private static final String ROLE_USER = "ROLE_USER";
    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeyDerivation keyDerivation;
    private final MasterKeyStore masterKeyStore;

    public MasterKeyAuthenticationProvider(UserRepository userRepository,
                                           PasswordEncoder passwordEncoder,
                                           KeyDerivation keyDerivation,
                                           MasterKeyStore masterKeyStore) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.keyDerivation = keyDerivation;
        this.masterKeyStore = masterKeyStore;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = String.valueOf(authentication.getCredentials());

        Optional<User> found = userRepository.findByUsernameIgnoreCase(username);
        if (found.isEmpty() || !passwordEncoder.matches(password, found.get().getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS);
        }

        User user = found.get();
        masterKeyStore.unlock(keyDerivation.deriveMasterKey(password.toCharArray(), user.getKeySalt()));

        return new UsernamePasswordAuthenticationToken(user.getUsername(), null,
                List.of(new SimpleGrantedAuthority(ROLE_USER)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
