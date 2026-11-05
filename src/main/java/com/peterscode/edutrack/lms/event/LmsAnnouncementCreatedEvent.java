package com.peterscode.edutrack.lms.event;

public record LmsAnnouncementCreatedEvent(Long schoolId, Long announcementId, Long courseId,
                                          Long streamId, Long postedBy, boolean postedByTeacher,
                                          String title) {
}
