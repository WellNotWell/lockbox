package dev.lockbox.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RetiredPasswordRepository extends JpaRepository<RetiredPassword, Long> {

    List<RetiredPassword> findByUserIdOrderByRetiredAtDesc(Long userId);
}
