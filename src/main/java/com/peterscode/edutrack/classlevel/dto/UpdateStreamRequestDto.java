package com.peterscode.edutrack.classlevel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStreamRequestDto {

    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Size(max = 20, message = "Code cannot exceed 20 characters")
    private String code;

    @Min(value = 0, message = "Capacity cannot be negative")
    private Integer capacity;

    private Boolean active;
}
