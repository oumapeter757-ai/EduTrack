package com.peterscode.edutrack.notification.controller;

import com.peterscode.edutrack.notification.service.NotificationPreferenceService;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.NotificationChannel;
import com.peterscode.edutrack.security.RequiresModule;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.school.enums.ServiceModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationPreferenceService preferenceService;
    private final JdbcTemplate jdbc;

    public NotificationController(NotificationPreferenceService preferenceService,
                                  JdbcTemplate jdbc) {
        this.preferenceService = preferenceService;
        this.jdbc = jdbc;
    }

    @GetMapping("/preferences")
    @RequiresModule(ServiceModule.NOTIFICATIONS)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'LIBRARIAN', 'ACCOUNTANT')")
    public Map<String, Boolean> getPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        Map<NotificationChannel, Boolean> preferences = preferenceService.getOptionalPreferences(principal);
        Map<String, Boolean> response = new java.util.LinkedHashMap<>();
        preferences.forEach((channel, enabled) -> response.put(channel.name(), enabled));
        return response;
    }

    @PutMapping("/preferences/{channel}")
    @RequiresModule(ServiceModule.NOTIFICATIONS)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER', 'STUDENT', 'PARENT', 'LIBRARIAN', 'ACCOUNTANT')")
    public void updatePreference(@AuthenticationPrincipal UserPrincipal principal,
                                 @PathVariable NotificationChannel channel,
                                 @Valid @RequestBody PreferenceRequest request) {
        preferenceService.updateOptionalPreference(principal, channel, request.enabled());
    }

    @PostMapping("/opt-out")
    @PreAuthorize("permitAll()")
    public void optOut(@Valid @RequestBody OptOutRequest request) {
        applyOptOut(request.token());
    }

    @GetMapping("/opt-out")
    @PreAuthorize("permitAll()")
    public String optOutFromLink(@RequestParam String token) {
        applyOptOut(token);
        return "You have been unsubscribed from optional notifications.";
    }

    private void applyOptOut(String token) {
        try {
            preferenceService.optOut(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Opt-out link is invalid or expired");
        }
    }

    @GetMapping("/summary")
    @RequiresModule(ServiceModule.NOTIFICATIONS)
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public Map<String, Long> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getSchoolId() == null) {
            throw new IllegalStateException("A school-scoped administrator is required");
        }
        Map<String, Long> result = jdbc.query("""
                SELECT status, COUNT(*) AS amount FROM notification_messages
                WHERE school_id = ? AND created_at >= DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY)
                GROUP BY status
                """, rs -> {
            Map<String, Long> counts = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getLong("amount"));
            }
            return counts;
        }, principal.getSchoolId());
        Long reserved = jdbc.queryForObject("""
                SELECT COALESCE(MAX(reserved_segments), 0) FROM notification_sms_usage
                WHERE school_id = ? AND billing_month = CAST(DATE_FORMAT(UTC_DATE(), '%Y-%m-01') AS DATE)
                """, Long.class, principal.getSchoolId());
        Long sent = jdbc.queryForObject("""
                SELECT COALESCE(MAX(sent_segments), 0) FROM notification_sms_usage
                WHERE school_id = ? AND billing_month = CAST(DATE_FORMAT(UTC_DATE(), '%Y-%m-01') AS DATE)
                """, Long.class, principal.getSchoolId());
        result.put("SMS_RESERVED_SEGMENTS", reserved == null ? 0L : reserved);
        result.put("SMS_SENT_SEGMENTS", sent == null ? 0L : sent);
        return result;
    }

    public record PreferenceRequest(@NotNull Boolean enabled) {
    }

    public record OptOutRequest(@NotBlank String token) {
    }

}
