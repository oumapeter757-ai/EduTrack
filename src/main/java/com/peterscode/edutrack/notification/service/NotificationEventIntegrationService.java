package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.notification.entity.NotificationCategory;
import com.peterscode.edutrack.notification.service.NotificationMessageComposer.Recipient;
import com.peterscode.edutrack.notification.service.NotificationOutboxStore.EnqueueMessage;
import com.peterscode.edutrack.attendance.event.StudentAbsenceRecordedEvent;
import com.peterscode.edutrack.lms.event.LmsAnnouncementCreatedEvent;
import com.peterscode.edutrack.security.ModuleAccessService;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.timetable.event.TimetablePublishedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NotificationEventIntegrationService {

    private final NotificationRecipientResolver recipientResolver;
    private final NotificationMessageComposer composer;
    private final NotificationOutboxService outbox;
    private final ModuleAccessService moduleAccess;
    private final NotificationAuditStore audit;
    private final boolean teacherAnnouncementsEnabled;

    public NotificationEventIntegrationService(NotificationRecipientResolver recipientResolver,
                                               NotificationMessageComposer composer,
                                               NotificationOutboxService outbox,
                                               ModuleAccessService moduleAccess,
                                               NotificationAuditStore audit,
                                               @Value("${edutrack.notifications.teacher-announcements-enabled:false}")
                                               boolean teacherAnnouncementsEnabled) {
        this.recipientResolver = recipientResolver;
        this.composer = composer;
        this.outbox = outbox;
        this.moduleAccess = moduleAccess;
        this.audit = audit;
        this.teacherAnnouncementsEnabled = teacherAnnouncementsEnabled;
    }

    @Transactional
    public void timetablePublished(TimetablePublishedEvent event) {
        if (event == null || event.schoolId() == null || event.timetableId() == null
                || !moduleAccess.isModuleEnabled(event.schoolId(), ServiceModule.NOTIFICATIONS)
                || !composer.isReadyFor(NotificationCategory.OPTIONAL)) {
            return;
        }
        String eventKey = "timetable-published:" + event.timetableId();
        long afterId = 0;
        while (true) {
            List<Recipient> recipients = recipientResolver.activeParentRecipients(event.schoolId(), afterId);
            if (recipients.isEmpty()) {
                return;
            }
            afterId = recipients.getLast().recipientId();
            List<EnqueueMessage> messages = composer.compose(event.schoolId(), recipients,
                    "School timetable published",
                    "A new timetable has been published by your school.", NotificationCategory.OPTIONAL);
            if (!messages.isEmpty()) {
                outbox.enqueue(event.schoolId(), "TIMETABLE_PUBLISHED", eventKey,
                        "{\"timetableId\":" + event.timetableId() + "}", messages);
                audit.record(event.schoolId(), null, "REQUEST_QUEUED", null, null);
            }
            if (recipients.size() < 500) {
                return;
            }
        }
    }

    @Transactional
    public void absencesRecorded(StudentAbsenceRecordedEvent event) {
        if (event == null || event.schoolId() == null || event.attendanceDate() == null
                || event.studentIds().isEmpty()
                || !moduleAccess.isModuleEnabled(event.schoolId(), ServiceModule.NOTIFICATIONS)
                || !composer.isReadyFor(NotificationCategory.OPTIONAL)) {
            return;
        }
        List<Recipient> recipients = recipientResolver.activeParentRecipientsForStudents(
                event.schoolId(), event.studentIds());
        List<EnqueueMessage> messages = composer.compose(event.schoolId(), recipients,
                "Attendance update",
                "Attendance for one or more linked students was marked absent on "
                        + event.attendanceDate() + ". Please contact the school if this is unexpected.",
                NotificationCategory.OPTIONAL);
        if (!messages.isEmpty()) {
            outbox.enqueue(event.schoolId(), "STUDENT_ABSENCE_RECORDED",
                    absenceEventKey(event),
                    "{\"attendanceDate\":\"" + event.attendanceDate() + "\"}", messages);
            audit.record(event.schoolId(), null, "REQUEST_QUEUED", null, null);
        }
    }

    private String absenceEventKey(StudentAbsenceRecordedEvent event) {
        String studentIds = event.studentIds().stream().distinct().sorted()
                .map(String::valueOf).collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(studentIds.getBytes(StandardCharsets.UTF_8));
            return "student-absence:" + event.attendanceDate() + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @Transactional
    public void lmsAnnouncementCreated(LmsAnnouncementCreatedEvent event) {
        if (event == null || event.schoolId() == null || event.announcementId() == null
                || event.streamId() == null
                || (event.postedByTeacher() && !teacherAnnouncementsEnabled)
                || !moduleAccess.isModuleEnabled(event.schoolId(), ServiceModule.NOTIFICATIONS)
                || !composer.isReadyFor(NotificationCategory.OPTIONAL)) {
            return;
        }
        List<Recipient> recipients = recipientResolver.activeParentRecipientsForStream(
                event.schoolId(), event.streamId());
        String title = event.title() == null ? "Course announcement"
                : event.title().replaceAll("[\\r\\n]+", " ").trim();
        List<EnqueueMessage> messages = composer.compose(event.schoolId(), recipients,
                "Course announcement: " + title,
                "A new announcement was posted in your linked student's stream. Open EduTrack to view it.",
                NotificationCategory.OPTIONAL);
        if (!messages.isEmpty()) {
            outbox.enqueue(event.schoolId(), "LMS_ANNOUNCEMENT",
                    "lms-announcement:" + event.announcementId(),
                    "{\"courseId\":" + event.courseId() + ",\"streamId\":" + event.streamId() + "}",
                    messages);
            audit.record(event.schoolId(), event.postedBy(), "ANNOUNCEMENT_NOTIFICATION_QUEUED", null, null);
        }
    }
}
