package com.peterscode.edutrack.student.dto;

import com.peterscode.edutrack.student.enums.StudentStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateStudentStatusRequestDto {

    @NotNull(message = "Student status is required")
    private StudentStatus status;

    private Boolean active;
}
