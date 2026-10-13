package com.peterscode.edutrack.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDto {

    private String token;
    @Builder.Default
    private String type = "Bearer";
    private String email;
    private String role;
    private Long schoolId;
}