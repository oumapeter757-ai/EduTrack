package com.peterscode.edutrack.school.repository;

import com.peterscode.edutrack.school.entity.School;
import com.peterscode.edutrack.school.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long>, JpaSpecificationExecutor<School> {

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
}
