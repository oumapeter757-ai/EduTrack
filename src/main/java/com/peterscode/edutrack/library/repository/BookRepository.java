package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    Optional<Book> findBySchoolIdAndId(Long schoolId, Long id);

    @Query("SELECT b FROM Book b WHERE b.schoolId = :schoolId AND b.active = true")
    Page<Book> findBySchoolIdAndActiveTrue(@Param("schoolId") Long schoolId, Pageable pageable);

    @Query("""
        SELECT DISTINCT b FROM Book b
        WHERE b.schoolId = :schoolId
          AND b.active = true
          AND b.lendable = true
          AND EXISTS (
              SELECT bc FROM BookCopy bc
              WHERE bc.bookId = b.id
                AND bc.status = 'AVAILABLE'
          )
    """)
    Page<Book> findAvailableBooks(@Param("schoolId") Long schoolId, Pageable pageable);

    @Query("""
        SELECT DISTINCT b FROM Book b
        WHERE b.schoolId = :schoolId
          AND b.active = true
          AND b.lendable = true
          AND EXISTS (
              SELECT bc FROM BookCopy bc
              WHERE bc.bookId = b.id
                AND bc.status = 'AVAILABLE'
          )
          AND (:categoryId IS NULL OR b.categoryId = :categoryId)
          AND (:keyword IS NULL OR
               LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(b.authors) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:isbn IS NULL OR b.isbn = :isbn)
          AND (:language IS NULL OR b.language = :language)
    """)
    Page<Book> searchAvailableBooks(
            @Param("schoolId") Long schoolId,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            @Param("isbn") String isbn,
            @Param("language") String language,
            Pageable pageable
    );
}
