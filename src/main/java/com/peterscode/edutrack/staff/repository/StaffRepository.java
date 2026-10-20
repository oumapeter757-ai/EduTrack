package com.peterscode.edutrack.staff.repository;

import com.peterscode.edutrack.staff.entity.Staff;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;

public interface StaffRepository extends JpaRepository<Staff, Long> {
  Optional<Staff> findByIdAndSchoolId(Long id, Long schoolId);

  Page<Staff> findBySchoolId(Long schoolId, org.springframework.data.domain.Pageable p);

  Optional<Staff> findBySchoolIdAndStaffNumber(Long s, String n);

  boolean existsBySchoolIdAndStaffNumber(Long s, String n);

  boolean existsBySchoolIdAndStaffNumberAndIdNot(Long s, String n, Long id);

  boolean existsBySchoolIdAndEmail(Long s, String e);

  boolean existsBySchoolIdAndEmailAndIdNot(Long s, String e, Long id);
}
