package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.BorrowingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BorrowingPolicyRepository extends JpaRepository<BorrowingPolicy, Long> {

    @Query("SELECT p FROM BorrowingPolicy p WHERE p.schoolId = :schoolId AND p.borrowerType = :borrowerType")
    Optional<BorrowingPolicy> findBySchoolIdAndBorrowerType(
            @Param("schoolId") Long schoolId, @Param("borrowerType") String borrowerType);
}
