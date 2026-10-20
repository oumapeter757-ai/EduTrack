package com.peterscode.edutrack.subject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectResponseDto {

    private Long id;
    private Long schoolId;
    private Long classLevelId;
    private String classLevelName;
    private String name;
    private String code;
    private String description;
    private String department;
    private Integer creditHours;

    @JsonProperty("isElective")
    private boolean isElective;

    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isElective() {
        return isElective;
    }

    public boolean getIsElective() {
        return isElective;
    }

    public void setIsElective(boolean isElective) {
        this.isElective = isElective;
    }

    public void setElective(boolean isElective) {
        this.isElective = isElective;
    }
}
