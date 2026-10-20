package com.peterscode.edutrack.subject.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = false)
public class UpdateSubjectRequestDto {

    private Long classLevelId;

    @Size(max = 100, message = "Subject name must not exceed 100 characters")
    private String name;

    @Size(max = 20, message = "Subject code must not exceed 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]+$", message = "Subject code must be alphanumeric")
    private String code;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;

    @Positive(message = "Credit hours must be positive")
    private Integer creditHours;

    @JsonProperty("isElective")
    private Boolean isElective;

    private Boolean active;

    public Boolean getIsElective() {
        return isElective;
    }

    public void setIsElective(Boolean isElective) {
        this.isElective = isElective;
    }
}
