package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.attendance.event.StudentAbsenceRecordedEvent;
import com.peterscode.edutrack.timetable.event.TimetablePublishedEvent;
import com.peterscode.edutrack.lms.event.LmsAnnouncementCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TimetableNotificationListener {

    private final NotificationEventIntegrationService integrations;

    public TimetableNotificationListener(NotificationEventIntegrationService integrations) {
        this.integrations = integrations;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTimetablePublished(TimetablePublishedEvent event) {
        integrations.timetablePublished(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAbsenceRecorded(StudentAbsenceRecordedEvent event) {
        integrations.absencesRecorded(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLmsAnnouncementCreated(LmsAnnouncementCreatedEvent event) {
        integrations.lmsAnnouncementCreated(event);
    }
}
