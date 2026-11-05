package com.peterscode.edutrack.notification.service;

import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.notification.service.NotificationPreferenceStore.PreferenceIdentity;
import com.peterscode.edutrack.notification.entity.NotificationRecipientType;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.user.entity.Role;
import org.springframework.stereotype.Service;

@Service
public class NotificationIdentityResolver {

    private final GuardianRepository guardians;
    private final StudentRepository students;
    private final StaffRepository staff;

    public NotificationIdentityResolver(GuardianRepository guardians,
                                        StudentRepository students,
                                        StaffRepository staff) {
        this.guardians = guardians;
        this.students = students;
        this.staff = staff;
    }

    public PreferenceIdentity resolve(UserPrincipal principal) {
        if (principal == null || principal.getId() == null || principal.getSchoolId() == null) {
            throw new IllegalStateException("A school-scoped authenticated user is required");
        }
        long schoolId = principal.getSchoolId();
        long userId = principal.getId();
        Role role = principal.getRole();

        if (role == Role.PARENT) {
            return guardians.findBySchoolIdAndUserId(schoolId, userId)
                    .map(guardian -> new PreferenceIdentity(NotificationRecipientType.PARENT, guardian.getId()))
                    .orElseThrow(() -> new IllegalStateException("Parent profile is not available"));
        }
        if (role == Role.STUDENT) {
            return students.findBySchoolIdAndUserId(schoolId, userId)
                    .map(student -> new PreferenceIdentity(NotificationRecipientType.STUDENT, student.getId()))
                    .orElseThrow(() -> new IllegalStateException("Student profile is not available"));
        }
        if (role == Role.TEACHER || role == Role.LIBRARIAN || role == Role.ACCOUNTANT) {
            return staff.findBySchoolIdAndUserId(schoolId, userId)
                    .map(member -> new PreferenceIdentity(NotificationRecipientType.STAFF, member.getId()))
                    .orElseThrow(() -> new IllegalStateException("Staff profile is not available"));
        }
        if (role == Role.SCHOOL_ADMIN) {
            return new PreferenceIdentity(NotificationRecipientType.USER, userId);
        }
        throw new IllegalStateException("This role does not have school notification preferences");
    }
}
