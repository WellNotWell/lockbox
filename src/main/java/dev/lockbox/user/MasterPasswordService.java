package dev.lockbox.user;

import dev.lockbox.crypto.KeyDerivation;
import dev.lockbox.crypto.KeyEnvelope;
import dev.lockbox.vault.Entry;
import dev.lockbox.vault.EntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.List;

@Service
public class MasterPasswordService {

    private static final Logger log = LoggerFactory.getLogger(MasterPasswordService.class);

    static final int REMEMBERED_PASSWORDS = 5;

    private final UserRepository userRepository;
    private final RetiredPasswordRepository retiredPasswordRepository;
    private final EntryRepository entryRepository;
    private final PasswordEncoder passwordEncoder;
    private final KeyDerivation keyDerivation;
    private final KeyEnvelope keyEnvelope;

    public MasterPasswordService(UserRepository userRepository,
                                 RetiredPasswordRepository retiredPasswordRepository,
                                 EntryRepository entryRepository,
                                 PasswordEncoder passwordEncoder,
                                 KeyDerivation keyDerivation,
                                 KeyEnvelope keyEnvelope) {
        this.userRepository = userRepository;
        this.retiredPasswordRepository = retiredPasswordRepository;
        this.entryRepository = entryRepository;
        this.passwordEncoder = passwordEncoder;
        this.keyDerivation = keyDerivation;
        this.keyEnvelope = keyEnvelope;
    }

    @Transactional
    public SecretKey change(User user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new WrongPasswordException();
        }
        if (wasUsedBefore(user, newPassword)) {
            throw new PasswordAlreadyUsedException();
        }

        SecretKey currentKey = keyDerivation.deriveMasterKey(currentPassword.toCharArray(), user.getKeySalt());
        byte[] newSalt = keyDerivation.newSalt();
        SecretKey newKey = keyDerivation.deriveMasterKey(newPassword.toCharArray(), newSalt);

        List<Entry> entries = entryRepository.findByOwnerIdOrderByTitle(user.getId());
        for (Entry entry : entries) {
            SecretKey dataKey = keyEnvelope.unwrap(entry.getDataKey(), currentKey);
            entry.setDataKey(keyEnvelope.wrap(dataKey, newKey));
        }

        remember(user, user.getPasswordHash());
        user.setKeySalt(newSalt);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Master password changed, rewrapped {} entry keys", entries.size());
        return newKey;
    }

    private boolean wasUsedBefore(User user, String candidate) {
        if (passwordEncoder.matches(candidate, user.getPasswordHash())) {
            return true;
        }
        return history(user).stream()
                .anyMatch(retired -> passwordEncoder.matches(candidate, retired.getPasswordHash()));
    }

    private void remember(User user, String retiredHash) {
        RetiredPassword retired = new RetiredPassword();
        retired.setUser(user);
        retired.setPasswordHash(retiredHash);
        retiredPasswordRepository.save(retired);

        List<RetiredPassword> kept = history(user);
        if (kept.size() >= REMEMBERED_PASSWORDS) {
            retiredPasswordRepository.deleteAll(kept.subList(REMEMBERED_PASSWORDS - 1, kept.size()));
        }
    }

    private List<RetiredPassword> history(User user) {
        return retiredPasswordRepository.findByUserIdOrderByRetiredAtDesc(user.getId());
    }
}
