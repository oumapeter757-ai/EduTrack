package com.peterscode.edutrack.library.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

public class LibraryRequests {

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateCategoryRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String code
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateCategoryRequest(
            @Size(max = 100) String name,
            @Size(max = 50) String code,
            Boolean active
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateBookRequest(
            @NotBlank @Size(max = 500) String title,
            Long categoryId,
            @Size(max = 1000) String authors,
            @Size(max = 20) String isbn,
            @Size(max = 200) String publisher,
            Integer publicationYear,
            @Size(max = 50) String edition,
            @Size(max = 50) String language,
            Boolean lendable
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateBookRequest(
            @Size(max = 500) String title,
            Long categoryId,
            @Size(max = 1000) String authors,
            @Size(max = 20) String isbn,
            @Size(max = 200) String publisher,
            Integer publicationYear,
            @Size(max = 50) String edition,
            @Size(max = 50) String language,
            Boolean lendable,
            Boolean active
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateCopyRequest(
            Long bookId,
            @NotBlank @Size(max = 50) String bookNumber,
            @Size(max = 100) String shelfLocation,
            @Size(max = 50) String condition,
            String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record BatchCreateCopiesRequest(
            Long bookId,
            @NotBlank List<@NotBlank @Size(max = 50) String> bookNumbers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record UpdateCopyRequest(
            @Size(max = 100) String shelfLocation,
            @Size(max = 50) String condition,
            String status,
            Long version
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record IssueRequest(
            @NotBlank @Size(max = 50) String bookNumber,
            @NotBlank @Size(max = 50) String borrowerNumber
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ReturnRequest(
            @NotBlank @Size(max = 50) String bookNumber,
            String damage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AssessFineRequest(
            @jakarta.validation.constraints.DecimalMin("0.00") java.math.BigDecimal amount,
            @NotBlank String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WaiveFineRequest(
            @NotBlank @Size(max = 500) String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record PayFineRequest(
            @NotBlank @Size(max = 200) String reference
    ) {}
}
