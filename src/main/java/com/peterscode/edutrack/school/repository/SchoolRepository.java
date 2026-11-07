package com.peterscode.edutrack.school.repository;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long>, JpaSpecificationExecutor<School> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from School s where s.id = :id")
    Optional<School> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByCode(String code);

    boolean existsBySubdomain(String subdomain);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsBySubdomainAndIdNot(String subdomain, Long id);

    Optional<School> findByCode(String code);

    Optional<School> findBySubdomain(String subdomain);

    Optional<School> findBySubdomainAndActiveTrue(String subdomain);

    Optional<School> findByCodeAndActiveTrue(String code);

    long countBySubscriptionTier(SubscriptionTier tier);

    long countByActive(boolean active);

    /**
     * Fetch a school with its enabled modules eagerly joined.
     * Use this when you need {@code enabledModules} to avoid the N+1 query
     * caused by {@code FetchType.LAZY} on the collection.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT s FROM School s LEFT JOIN FETCH s.enabledModules WHERE s.id = :id")
    Optional<School> findByIdWithModules(@org.springframework.data.repository.query.Param("id") Long id);
}
