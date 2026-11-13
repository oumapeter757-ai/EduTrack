package com.peterscode.edutrack.auth.repository;

import com.peterscode.edutrack.auth.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM PasswordResetToken token JOIN FETCH token.user WHERE token.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken token SET token.usedAt = :usedAt " +
            "WHERE token.user.id = :userId AND token.usedAt IS NULL")
    int invalidateUnusedForUser(@Param("userId") Long userId, @Param("usedAt") LocalDateTime usedAt);
}
