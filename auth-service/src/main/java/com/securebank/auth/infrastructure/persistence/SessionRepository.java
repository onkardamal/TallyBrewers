package com.securebank.auth.infrastructure.persistence;

import com.securebank.auth.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    Optional<Session> findByRefreshTokenHash(String refreshTokenHash);

    List<Session> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}
