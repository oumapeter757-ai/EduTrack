package com.peterscode.edutrack.subject.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectSummaryDto {

    private Long id;
    private String name;
    private String code;
    private String department;

    @JsonProperty("isElective")
    private boolean isElective;

    private boolean active;

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
