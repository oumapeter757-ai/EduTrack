package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.StudentFeeAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface StudentFeeAccountRepository extends JpaRepository<StudentFeeAccount, Long> {

    Optional<StudentFeeAccount> findBySchoolIdAndStudentId(Long schoolId, Long studentId);

    @Lock(PESSIMISTIC_WRITE)
    Optional<StudentFeeAccount> findBySchoolIdAndId(Long schoolId, Long id);

    boolean existsBySchoolIdAndStudentId(Long schoolId, Long studentId);

    /**
     * Arrears by class level: joins student_fee_accounts with students to get class_level_id.
     * Only includes accounts with positive cached_balance (money owed).
     */
    @Query(value = """
            SELECT s.class_level_id, COUNT(sf.id) as cnt, SUM(sf.cached_balance) as total, sf.currency
            FROM student_fee_accounts sf
            INNER JOIN students s ON s.id = sf.student_id AND s.school_id = sf.school_id
            WHERE sf.school_id = :schoolId
              AND sf.cached_balance > 0
              AND s.status = 'ACTIVE'
            GROUP BY s.class_level_id, sf.currency
            ORDER BY s.class_level_id
            """, nativeQuery = true)
    List<Object[]> arrearsByClassLevel(@Param("schoolId") Long schoolId);
}
