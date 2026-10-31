package com.peterscode.edutrack.timetable.event;

/**
 * Fired after a timetable is published. Listeners should use
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to run after commit.
 */
public record TimetablePublishedEvent(Long timetableId, Long schoolId, Long termId) {
}
