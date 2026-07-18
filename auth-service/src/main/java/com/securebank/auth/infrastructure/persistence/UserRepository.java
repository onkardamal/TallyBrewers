package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByWebauthnUserHandle(UUID webauthnUserHandle);

    boolean existsByEmail(String email);
}
