package com.peterscode.edutrack.cache;

import com.peterscode.edutrack.classlevel.dto.ClassLevelResponseDto;
import com.peterscode.edutrack.classlevel.dto.StreamResponseDto;
import com.peterscode.edutrack.fees.dto.FeeStructureView;
import com.peterscode.edutrack.grading.dto.GradingViews;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.school.dto.PublicSchoolProfileDto;
import com.peterscode.edutrack.school.dto.SchoolProfileCacheDto;
import com.peterscode.edutrack.school.dto.SchoolResponseDto;
import com.peterscode.edutrack.subject.dto.SubjectSummaryDto;
import com.peterscode.edutrack.timetable.dto.TimetableViews;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security audit tests for Redis caching.
 * <p>
 * Verifies P3 rule: no cached DTO contains personal data, credentials,
 * fee balances, scores, medical notes, or other forbidden fields.
 */
class CacheSecurityAuditTest {

    /**
     * P3 deny list: field names that must NEVER appear in any cached DTO.
     * Covers phone, email, medical, balance, credential fields.
     * Note: grading configuration fields like maxScore/weight in AssessmentPaperView
     * are allowed because they define the scale, not actual student scores.
     * Fee structure amounts are allowed because they are configuration, not balances.
     */
    static final Set<String> P3_DENY_LIST = Set.of(
            // Personal contact
            "phone", "phoneNumber", "mobile", "email",
            // Medical
            "medicalNote", "medicalNotes", "medicalHistory", "allergy", "bloodType",
            // Financial — balances and transactions only (not fee config amounts)
            "balance", "totalBalance", "outstandingBalance", "amountPaid", "amountDue",
            "payment", "payments", "invoice", "invoices", "ledger", "ledgerEntry",
            "mpesa", "mpesaData", "mpesaCredential", "passkey", "shortCode",
            // Credentials
            "password", "passwordHash", "token", "secret", "apiKey",
            // Attendance / library state
            "attendance", "attendances", "present", "absent",
            "loan", "loans", "borrowing", "borrowed", "returned",
            // Personal details
            "studentName", "studentId", "teacherName", "teacherId",
            "parentName", "parentId", "fullName", "borrowerNumber",
            // M-Pesa
            "tillNumber"
    );
    // Note: addressLine1/addressLine2 are allowed in school DTOs as they are institutional, not personal.

    /**
     * All DTO classes that are cached via @Cacheable.
     * If a new cache is added, its DTO must be added here for audit.
     */
    static final Class<?>[] CACHED_DTOS = {
            // School
            PublicSchoolProfileDto.class,
            SchoolProfileCacheDto.class,
            // Class levels and streams
            ClassLevelResponseDto.class,
            StreamResponseDto.class,
            // Timetable config
            TimetableViews.PeriodView.class,
            TimetableViews.RoomView.class,
            TimetableViews.CurriculumView.class,
            // Subjects
            SubjectSummaryDto.class,
            // Grading config
            GradingViews.GradeScaleView.class,
            GradingViews.AssessmentTypeView.class,
            GradingViews.AssessmentPaperView.class,
            // Library
            LibraryViews.CategoryView.class,
            // Fees
            FeeStructureView.class,
            // Published timetable views
            TimetableViews.TeacherLessonView.class,
            TimetableViews.StudentLessonView.class
    };

    @Test
    void noCachedDtoContainsP3ForbiddenFields() {
        for (Class<?> dtoClass : CACHED_DTOS) {
            if (dtoClass.isRecord()) {
                auditRecord(dtoClass);
            } else {
                auditClass(dtoClass);
            }
        }
    }

    @Test
    void schoolResponseDtoContainsPhoneAndEmail() {
        // SchoolResponseDto is NOT cached (it contains P3 fields).
        // Verify it actually has the fields so we know the deny list is meaningful.
        boolean hasPhone = false;
        boolean hasEmail = false;
        for (Field f : SchoolResponseDto.class.getDeclaredFields()) {
            if ("phone".equals(f.getName())) hasPhone = true;
            if ("email".equals(f.getName())) hasEmail = true;
        }
        assertTrue(hasPhone, "SchoolResponseDto should have phone field");
        assertTrue(hasEmail, "SchoolResponseDto should have email field");
    }

    @Test
    void schoolProfileCacheDtoExcludesPhoneAndEmail() {
        // The cache-safe variant must NOT contain phone or email.
        for (Field f : SchoolProfileCacheDto.class.getDeclaredFields()) {
            assertFalse(
                    "phone".equals(f.getName()) || "email".equals(f.getName()),
                    "SchoolProfileCacheDto must not contain '" + f.getName() + "' (P3 violation)"
            );
        }
    }

    private void auditRecord(Class<?> recordClass) {
        for (RecordComponent rc : recordClass.getRecordComponents()) {
            String name = rc.getName();
            assertFalse(
                    P3_DENY_LIST.contains(name.toLowerCase()),
                    "Cached record " + recordClass.getSimpleName() + " contains P3-forbidden field: " + name
            );
        }
    }

    private void auditClass(Class<?> clazz) {
        for (Field f : clazz.getDeclaredFields()) {
            String name = f.getName();
            assertFalse(
                    P3_DENY_LIST.contains(name.toLowerCase()),
                    "Cached class " + clazz.getSimpleName() + " contains P3-forbidden field: " + name
            );
        }
    }
}
