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
import com.peterscode.edutrack.student.enums.Gender;

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

    @org.springframework.data.jpa.repository.Query("select s.status as key, count(s) as value from Student s where s.schoolId = :schoolId group by s.status")
    List<Object[]> countGroupedByStatus(Long schoolId);

    @org.springframework.data.jpa.repository.Query("select s.gender as key, count(s) as value from Student s where s.schoolId = :schoolId group by s.gender")
    List<Object[]> countGroupedByGender(Long schoolId);

    @org.springframework.data.jpa.repository.Query("select s.gradeLevel as key, count(s) as value from Student s where s.schoolId = :schoolId and s.gradeLevel is not null group by s.gradeLevel")
    List<Object[]> countGroupedByGradeLevel(Long schoolId);

    @org.springframework.data.jpa.repository.Query("select s.classLevelId, count(s) from Student s where s.schoolId = :schoolId group by s.classLevelId")
    List<Object[]> countGroupedByClassLevel(Long schoolId);
}
