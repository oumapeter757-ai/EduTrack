package com.peterscode.edutrack.enrollment.repository;

import com.peterscode.edutrack.enrollment.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface PromotionRunRepository extends JpaRepository<PromotionRun, Long> {
    Optional<PromotionRun> findBySchoolIdAndId(Long schoolId, Long id);
    Page<PromotionRun> findBySchoolId(Long schoolId, Pageable pageable);
    boolean existsBySchoolIdAndOpenMarker(Long schoolId, Integer marker);
}
