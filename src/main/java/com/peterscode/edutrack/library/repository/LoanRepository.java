package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.Loan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Query("SELECT l FROM Loan l WHERE l.copyId = :copyId AND l.activeMarker IS NOT NULL")
    Optional<Loan> findActiveByCopyId(@Param("copyId") Long copyId);

    @Query("SELECT l FROM Loan l WHERE l.schoolId = :schoolId AND l.borrowerType = :borrowerType AND l.borrowerId = :borrowerId AND l.activeMarker IS NOT NULL")
    List<Loan> findActiveByBorrower(@Param("schoolId") Long schoolId,
                                     @Param("borrowerType") String borrowerType,
                                     @Param("borrowerId") Long borrowerId);

    @Query("SELECT COUNT(l) FROM Loan l WHERE l.schoolId = :schoolId AND l.borrowerType = :borrowerType AND l.borrowerId = :borrowerId AND l.activeMarker IS NOT NULL")
    long countActiveByBorrower(@Param("schoolId") Long schoolId,
                                @Param("borrowerType") String borrowerType,
                                @Param("borrowerId") Long borrowerId);

    @Query("SELECT l FROM Loan l WHERE l.schoolId = :schoolId AND l.borrowerType = :borrowerType AND l.borrowerId = :borrowerId AND l.activeMarker IS NOT NULL AND l.dueDate < :today")
    List<Loan> findOverdueByBorrower(@Param("schoolId") Long schoolId,
                                      @Param("borrowerType") String borrowerType,
                                      @Param("borrowerId") Long borrowerId,
                                      @Param("today") LocalDate today);

    @Query("SELECT l FROM Loan l WHERE l.schoolId = :schoolId AND l.borrowerType = :borrowerType AND l.borrowerId = :borrowerId ORDER BY l.dueDate DESC")
    Page<Loan> findByBorrower(@Param("schoolId") Long schoolId,
                               @Param("borrowerType") String borrowerType,
                               @Param("borrowerId") Long borrowerId,
                               Pageable pageable);

    @Query("SELECT l FROM Loan l WHERE l.schoolId = :schoolId AND l.dueDate < :today AND l.activeMarker IS NOT NULL")
    Page<Loan> findOverdue(@Param("schoolId") Long schoolId, @Param("today") LocalDate today, Pageable pageable);

    Optional<Loan> findBySchoolIdAndId(Long schoolId, Long id);
}
