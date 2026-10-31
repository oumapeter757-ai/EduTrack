package com.peterscode.edutrack.guardian.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentChildSummary {

    private Long studentId;
    private String studentName;
    private String className;
    private String streamName;
    private String status;
}
