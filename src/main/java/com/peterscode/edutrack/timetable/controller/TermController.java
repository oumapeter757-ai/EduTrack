package com.peterscode.edutrack.timetable.controller;

import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.ApiResponse;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.TermRequest;
import com.peterscode.edutrack.timetable.dto.TimetableViews.TermView;
import com.peterscode.edutrack.timetable.service.TimetableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@NoModuleRequired
@RequestMapping("/api/v1/terms")
@RequiredArgsConstructor
public class TermController {
    private final TimetableService service;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ApiResponse<TermView>> createTerm(
            @Valid @RequestBody TermRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Term created", service.createTerm(request, principal)));
    }
}
