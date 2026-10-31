package com.peterscode.edutrack.timetable.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for timetable publish events after the transaction commits.
 * This ensures the event is processed only after the timetable status change is persisted.
 */
@Component
public class TimetablePublishedEventListener {

    private static final Logger log = LoggerFactory.getLogger(TimetablePublishedEventListener.class);

    @TransactionalEventListener
    public void onTimetablePublished(TimetablePublishedEvent event) {
        log.info("Timetable {} published for school {} term {}", event.timetableId(), event.schoolId(), event.termId());
    }
}
