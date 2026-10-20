package com.peterscode.edutrack.classlevel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamResponseDto {

    private Long id;
    private Long schoolId;
    private Long classLevelId;
    private String classLevelName;
    private String name;
    private String code;
    private Integer capacity;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
