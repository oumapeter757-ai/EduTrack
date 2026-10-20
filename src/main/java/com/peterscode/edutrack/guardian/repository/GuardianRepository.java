package com.peterscode.edutrack.guardian.repository;

import com.peterscode.edutrack.guardian.entity.Guardian;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.*;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {
  Optional<Guardian> findByIdAndSchoolId(Long id, Long schoolId);

  Page<Guardian> findBySchoolId(Long schoolId, org.springframework.data.domain.Pageable p);

  boolean existsBySchoolIdAndEmail(Long s, String e);

  boolean existsBySchoolIdAndEmailAndIdNot(Long s, String e, Long id);
}
