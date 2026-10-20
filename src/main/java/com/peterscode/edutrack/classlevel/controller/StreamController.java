package com.peterscode.edutrack.classlevel.controller;

import com.peterscode.edutrack.classlevel.dto.*;
import com.peterscode.edutrack.classlevel.service.StreamService;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.shared.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.peterscode.edutrack.security.NoModuleRequired;

import java.util.List;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamController {

    private final StreamService streamService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StreamResponseDto>> createStream(
            @Valid @RequestBody CreateStreamRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        StreamResponseDto response = streamService.createStream(requestDto, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Stream created successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    public ResponseEntity<ApiResponse<PageResponse<StreamResponseDto>>> getStreams(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long classLevelId,
            Pageable pageable, @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<StreamResponseDto> response = streamService.getStreams(schoolId, classLevelId, pageable, principal);
        return ResponseEntity.ok(ApiResponse.success("Streams retrieved successfully", response));
    }

    @GetMapping("/class-level/{classLevelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<StreamResponseDto>>> getStreamsByClassLevel(
            @PathVariable Long classLevelId, @AuthenticationPrincipal UserPrincipal principal) {
        List<StreamResponseDto> response = streamService.getStreamsByClassLevel(classLevelId, principal);
        return ResponseEntity.ok(ApiResponse.success("Streams retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<StreamResponseDto>> getStreamById(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        StreamResponseDto response = streamService.getStreamById(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Stream retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<StreamResponseDto>> updateStream(
            @PathVariable Long id, @Valid @RequestBody UpdateStreamRequestDto requestDto,
            @AuthenticationPrincipal UserPrincipal principal) {
        StreamResponseDto response = streamService.updateStream(id, requestDto, principal);
        return ResponseEntity.ok(ApiResponse.success("Stream updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteStream(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        streamService.deleteStream(id, principal);
        return ResponseEntity.ok(ApiResponse.success("Stream deleted successfully", null));
    }
}
