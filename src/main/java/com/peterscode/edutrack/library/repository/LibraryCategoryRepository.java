package com.peterscode.edutrack.library.repository;

import com.peterscode.edutrack.library.entity.LibraryCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LibraryCategoryRepository extends JpaRepository<LibraryCategory, Long> {

    boolean existsBySchoolIdAndCode(Long schoolId, String code);

    boolean existsBySchoolIdAndName(Long schoolId, String name);

    @Query("SELECT c FROM LibraryCategory c WHERE c.schoolId = :schoolId AND c.active = true")
    Page<LibraryCategory> findBySchoolIdAndActiveTrue(@Param("schoolId") Long schoolId, Pageable pageable);

    Optional<LibraryCategory> findBySchoolIdAndId(Long schoolId, Long id);

    Optional<LibraryCategory> findBySchoolIdAndCode(Long schoolId, String code);
}
