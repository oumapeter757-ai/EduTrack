package com.peterscode.edutrack.shared;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findBySchoolIdAndUserIdAndEndpointAndKey(
            Long schoolId, Long userId, String endpoint, String key);

    boolean existsBySchoolIdAndUserIdAndEndpointAndKey(
            Long schoolId, Long userId, String endpoint, String key);

    /**
     * Clean up expired rows in bounded batches.
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :now")
    int deleteByExpiresAtBefore(@Param("now") LocalDateTime now);
}
