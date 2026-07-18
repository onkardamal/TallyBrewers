package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.RecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
