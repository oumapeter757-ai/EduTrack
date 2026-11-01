package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.LibraryFine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface LibraryFineRepository extends JpaRepository<LibraryFine, Long> {

    @Query("SELECT f FROM LibraryFine f WHERE f.schoolId = :schoolId AND f.borrowerType = :borrowerType AND f.borrowerId = :borrowerId AND f.status IN ('PENDING_ASSESSMENT', 'UNPAID')")
    List<LibraryFine> findUnresolvedByBorrower(@Param("schoolId") Long schoolId,
                                                @Param("borrowerType") String borrowerType,
                                                @Param("borrowerId") Long borrowerId);

    @Query("SELECT COALESCE(SUM(f.amount), 0) FROM LibraryFine f WHERE f.schoolId = :schoolId AND f.borrowerType = :borrowerType AND f.borrowerId = :borrowerId AND f.status IN ('PENDING_ASSESSMENT', 'UNPAID') AND f.amount IS NOT NULL")
    BigDecimal sumUnpaidByBorrower(@Param("schoolId") Long schoolId,
                                    @Param("borrowerType") String borrowerType,
                                    @Param("borrowerId") Long borrowerId);

    @Query("SELECT f FROM LibraryFine f WHERE f.schoolId = :schoolId AND f.borrowerType = :borrowerType AND f.borrowerId = :borrowerId ORDER BY f.createdAt DESC")
    Page<LibraryFine> findByBorrower(@Param("schoolId") Long schoolId,
                                      @Param("borrowerType") String borrowerType,
                                      @Param("borrowerId") Long borrowerId,
                                      Pageable pageable);

    Optional<LibraryFine> findBySchoolIdAndId(Long schoolId, Long id);
}
