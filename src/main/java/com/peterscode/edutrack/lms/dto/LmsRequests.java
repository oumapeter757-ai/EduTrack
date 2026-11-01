package com.peterscode.edutrack.lms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LmsRequests {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateCourseRequest(
            @NotBlank @Size(max = 255) String title,
            Long streamSubjectId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateCourseRequest(
            @Size(max = 255) String title,
            String status
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateTopicRequest(
            @NotBlank @Size(max = 255) String title
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateResourceRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String type,
            String body,
            String url
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateResourceRequest(
            @Size(max = 255) String title,
            String body,
            String url
    ) {
    }
}
