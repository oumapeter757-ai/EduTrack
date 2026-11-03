package com.peterscode.edutrack.concurrency;

import com.peterscode.edutrack.examination.entity.ExamRegistration;
import com.peterscode.edutrack.examination.entity.ExamSeries;
import com.peterscode.edutrack.examination.repository.ExamRegistrationRepository;
import com.peterscode.edutrack.examination.repository.ExamSeriesRepository;
import com.peterscode.edutrack.grading.entity.AssessmentPaper;
import com.peterscode.edutrack.grading.repository.AssessmentPaperRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests for examination registration.
 * The unique constraint should prevent duplicates, but the service check is racy.
 */
@Tag("concurrency")
class ExaminationConcurrencyTest extends ConcurrencyTestBase {

    @Autowired
    private ExamRegistrationRepository registrationRepository;

    @Autowired
    private ExamSeriesRepository examSeriesRepository;

    @Autowired
    private AssessmentPaperRepository assessmentPaperRepository;

    @Autowired
    private StudentRepository studentRepository;

    private Long studentId;
    private Long examSeriesId;
    private Long assessmentPaperId;

    @org.junit.jupiter.api.BeforeEach
    void setupExam() {
        // Create a student
        Student student = Student.builder()
                .schoolId(schoolId)
                .firstName("Exam")
                .lastName("Student")
                .admissionNumber("EXM-" + schoolId + "-001")
                .status(com.peterscode.edutrack.student.enums.StudentStatus.ACTIVE)
                .build();
        student = studentRepository.save(student);
        studentId = student.getId();

        // Create exam series (requires termId, dates, createdBy)
        ExamSeries series = ExamSeries.builder()
                .schoolId(schoolId)
                .termId(1L)
                .name("Mid-Year Exams")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(1))
                .registrationOpensOn(LocalDate.now())
                .registrationClosesOn(LocalDate.now().plusDays(7))
                .status(ExamSeries.SeriesStatus.REGISTRATION_OPEN)
                .createdBy(adminUserId)
                .build();
        series = examSeriesRepository.save(series);
        examSeriesId = series.getId();

        // Create assessment paper (no schoolId field)
        AssessmentPaper paper = AssessmentPaper.builder()
                .assessmentTypeId(1L)
                .name("Math Paper")
                .code("MATH-1")
                .weight(BigDecimal.valueOf(0.4))
                .maxScore(BigDecimal.valueOf(100))
                .sortOrder(1)
                .build();
        paper = assessmentPaperRepository.save(paper);
        assessmentPaperId = paper.getId();
    }

    /**
     * Test: Two threads register the same student for the same series/paper.
     * Expected: One succeeds, one gets 409 from the unique constraint.
     */
    @Test
    void duplicateRegistration_shouldAllowOnlyOne() throws Exception {
        TestResult<Object> result = runConcurrent(2, () -> {
            ExamRegistration reg = ExamRegistration.builder()
                    .schoolId(schoolId)
                    .examSeriesId(examSeriesId)
                    .studentId(studentId)
                    .assessmentPaperId(assessmentPaperId)
                    .candidateNumber("CAND-" + schoolId + "-001")
                    .build();
            registrationRepository.save(reg);
            return "registered";
        }, 30);

        // Verify only one registration exists
        List<ExamRegistration> regs = registrationRepository.findAll().stream()
                .filter(r -> studentId.equals(r.getStudentId())
                        && examSeriesId.equals(r.getExamSeriesId())
                        && assessmentPaperId.equals(r.getAssessmentPaperId()))
                .toList();

        assertThat(regs.size())
                .as("Only one registration for this student/series/paper")
                .isEqualTo(1);
    }
}
