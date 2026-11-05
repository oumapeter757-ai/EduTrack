package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.service.NotificationMessageComposer.Recipient;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;
import java.util.ArrayList;

@Repository
public class NotificationRecipientResolver {

    private static final int PAGE_SIZE = 500;
    private final JdbcTemplate jdbc;

    public NotificationRecipientResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Recipient> activeParentRecipients(long schoolId, long afterGuardianId) {
        return jdbc.query("""
                SELECT DISTINCT g.id, g.email, g.phone
                FROM parent_student_mappings m
                JOIN guardians g ON g.id = m.parent_id AND g.school_id = m.school_id
                JOIN students s ON s.id = m.student_id AND s.school_id = m.school_id
                WHERE m.school_id = ? AND m.active = TRUE AND g.active = TRUE AND s.active = TRUE
                  AND g.id > ?
                ORDER BY g.id
                LIMIT ?
                """, (rs, rowNum) -> new Recipient(NotificationRecipientType.PARENT,
                rs.getLong("id"), rs.getString("email"), rs.getString("phone")),
                schoolId, afterGuardianId, PAGE_SIZE);
    }

    public List<Recipient> activeParentRecipientsForStudents(long schoolId, Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return List.of();
        }
        if (studentIds.size() > 10_000) {
            throw new IllegalArgumentException("Absence notification batch is too large");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(studentIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(schoolId);
        parameters.addAll(studentIds);
        return jdbc.query("""
                SELECT DISTINCT g.id, g.email, g.phone
                FROM parent_student_mappings m
                JOIN guardians g ON g.id = m.parent_id AND g.school_id = m.school_id
                JOIN students s ON s.id = m.student_id AND s.school_id = m.school_id
                WHERE m.school_id = ? AND m.active = TRUE AND g.active = TRUE AND s.active = TRUE
                  AND m.student_id IN (""" + placeholders + ") ORDER BY g.id LIMIT 10000",
                (rs, rowNum) -> new Recipient(NotificationRecipientType.PARENT,
                        rs.getLong("id"), rs.getString("email"), rs.getString("phone")),
                parameters.toArray());
    }

    public List<Recipient> activeParentRecipientsForStream(long schoolId, long streamId) {
        return jdbc.query("""
                SELECT DISTINCT g.id, g.email, g.phone
                FROM parent_student_mappings m
                JOIN guardians g ON g.id = m.parent_id AND g.school_id = m.school_id
                JOIN students s ON s.id = m.student_id AND s.school_id = m.school_id
                WHERE m.school_id = ? AND m.active = TRUE AND g.active = TRUE
                  AND s.active = TRUE AND s.stream_id = ?
                ORDER BY g.id
                LIMIT 10000
                """, (rs, rowNum) -> new Recipient(NotificationRecipientType.PARENT,
                rs.getLong("id"), rs.getString("email"), rs.getString("phone")),
                schoolId, streamId);
    }
}
