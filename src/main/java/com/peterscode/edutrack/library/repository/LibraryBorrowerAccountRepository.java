package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.LibraryBorrowerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LibraryBorrowerAccountRepository extends JpaRepository<LibraryBorrowerAccount, Long> {

    @Query("SELECT a FROM LibraryBorrowerAccount a WHERE a.schoolId = :schoolId AND a.borrowerType = :borrowerType AND a.borrowerId = :borrowerId")
    Optional<LibraryBorrowerAccount> findBySchoolIdAndBorrowerTypeAndBorrowerId(
            @Param("schoolId") Long schoolId,
            @Param("borrowerType") String borrowerType,
            @Param("borrowerId") Long borrowerId);

    @Modifying
    @Query(value = """
            INSERT INTO library_borrower_accounts (school_id, borrower_type, borrower_id, active_loan_count, version, created_at, updated_at)
            VALUES (:schoolId, :borrowerType, :borrowerId, 0, 0, NOW(), NOW())
            ON DUPLICATE KEY UPDATE version = version + 1
            """, nativeQuery = true)
    int upsertAccount(@Param("schoolId") Long schoolId,
                      @Param("borrowerType") String borrowerType,
                      @Param("borrowerId") Long borrowerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM LibraryBorrowerAccount a WHERE a.schoolId = :schoolId AND a.borrowerType = :borrowerType AND a.borrowerId = :borrowerId")
    Optional<LibraryBorrowerAccount> lockBySchoolIdAndBorrowerTypeAndBorrowerId(
            @Param("schoolId") Long schoolId,
            @Param("borrowerType") String borrowerType,
            @Param("borrowerId") Long borrowerId);
}
