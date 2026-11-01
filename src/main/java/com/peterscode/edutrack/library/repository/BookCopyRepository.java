package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.BookCopy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findBySchoolIdAndId(Long schoolId, Long id);

    Optional<BookCopy> findBySchoolIdAndBookNumber(Long schoolId, String bookNumber);

    boolean existsBySchoolIdAndBookNumber(Long schoolId, String bookNumber);

    List<BookCopy> findByBookIdAndSchoolId(Long bookId, Long schoolId);

    @Query("SELECT bc FROM BookCopy bc WHERE bc.bookId = :bookId AND bc.schoolId = :schoolId AND bc.status = 'AVAILABLE'")
    List<BookCopy> findAvailableByBookId(@Param("bookId") Long bookId, @Param("schoolId") Long schoolId);

    @Query("SELECT COUNT(bc) FROM BookCopy bc WHERE bc.bookId = :bookId AND bc.status = 'AVAILABLE'")
    long countAvailableByBookId(@Param("bookId") Long bookId);

    @Query("SELECT bc FROM BookCopy bc WHERE bc.schoolId = :schoolId AND bc.status = :status")
    Page<BookCopy> findBySchoolIdAndStatus(@Param("schoolId") Long schoolId, @Param("status") String status, Pageable pageable);
}
