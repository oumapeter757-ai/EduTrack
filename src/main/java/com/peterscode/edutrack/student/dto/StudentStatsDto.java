package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatsDto {

    private Long schoolId;
    private long totalStudents;
    private long activeStudents;
    private long inactiveStudents;
    private Integer maxCapacity;
    private Long capacityRemaining;
    private Double capacityUtilizationPercentage;
    private Map<StudentStatus, Long> statusCounts;
    private Map<String, Long> gradeLevelCounts;
    private Map<Gender, Long> genderCounts;
}
