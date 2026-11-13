package com.peterscode.edutrack.auth.repository;

import com.peterscode.edutrack.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM RefreshToken token JOIN FETCH token.user WHERE token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken token SET token.revokedAt = :revokedAt " +
            "WHERE token.familyId = :familyId AND token.revokedAt IS NULL")
    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") LocalDateTime revokedAt);

    @Modifying
    @Query("UPDATE RefreshToken token SET token.revokedAt = :revokedAt " +
            "WHERE token.user.id = :userId AND token.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
