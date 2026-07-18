package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.Passkey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PasskeyRepository extends JpaRepository<Passkey, Long> {

    List<Passkey> findByUserId(Long userId);

    Optional<Passkey> findByCredentialId(String credentialId);

    boolean existsByUserId(Long userId);
}
