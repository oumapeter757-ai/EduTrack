package com.peterscode.edutrack.guardian.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class LinkChildRequestDto {

    @NotNull(message = "Student ID is required")
    private Long studentId;

    @Size(max = 50)
    private String relationship;
}
