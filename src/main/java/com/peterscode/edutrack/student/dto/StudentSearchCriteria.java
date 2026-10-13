package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.BloodGroup;
import com.peterscode.edutrack.student.enums.Gender;
import com.peterscode.edutrack.student.enums.StudentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentSearchCriteria {

    private String search;
    private Long schoolId;
    private String gradeLevel;
    private String section;
    private String academicYear;
    private StudentStatus status;
    private Gender gender;
    private BloodGroup bloodGroup;
    private Boolean active;
}
