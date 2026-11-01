package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.SchoolPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SchoolPaymentConfigRepository extends JpaRepository<SchoolPaymentConfig, Long> {

    Optional<SchoolPaymentConfig> findBySchoolId(Long schoolId);

    Optional<SchoolPaymentConfig> findByShortcode(String shortcode);

    Optional<SchoolPaymentConfig> findByCallbackTokenHash(String callbackTokenHash);

    boolean existsByShortcodeAndSchoolIdNot(String shortcode, Long schoolId);
}
