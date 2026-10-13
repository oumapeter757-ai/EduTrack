package com.peterscode.edutrack.student.repository;

import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long>, JpaSpecificationExecutor<Student> {

    boolean existsBySchoolIdAndAdmissionNumber(Long schoolId, String admissionNumber);

    boolean existsBySchoolIdAndAdmissionNumberAndIdNot(Long schoolId, String admissionNumber, Long id);

    boolean existsBySchoolIdAndEmail(Long schoolId, String email);

    boolean existsBySchoolIdAndEmailAndIdNot(Long schoolId, String email, Long id);

    Optional<Student> findBySchoolIdAndId(Long schoolId, Long id);

    Optional<Student> findBySchoolIdAndAdmissionNumber(Long schoolId, String admissionNumber);

    Optional<Student> findBySchoolIdAndUserId(Long schoolId, Long userId);

    Optional<Student> findByUserId(Long userId);

    long countBySchoolId(Long schoolId);

    long countBySchoolIdAndActiveTrue(Long schoolId);

    long countBySchoolIdAndActiveFalse(Long schoolId);

    long countBySchoolIdAndStatus(Long schoolId, StudentStatus status);

    long countBySchoolIdAndGender(Long schoolId, Gender gender);

    long countBySchoolIdAndGradeLevel(Long schoolId, String gradeLevel);

    Page<Student> findBySchoolId(Long schoolId, Pageable pageable);

    List<Student> findBySchoolIdAndGradeLevel(Long schoolId, String gradeLevel);

    List<Student> findBySchoolIdAndGradeLevelAndSection(Long schoolId, String gradeLevel, String section);

    List<Student> findAllBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);
}
