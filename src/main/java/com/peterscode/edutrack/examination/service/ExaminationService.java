package com.peterscode.edutrack.examination.service;

import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.examination.dto.*;
import com.peterscode.edutrack.examination.entity.ExamRegistration;
import com.peterscode.edutrack.examination.entity.ExamSeries;
import com.peterscode.edutrack.examination.exception.ExaminationRuleException;
import com.peterscode.edutrack.examination.repository.ExamRegistrationRepository;
import com.peterscode.edutrack.examination.repository.ExamSeriesRepository;
import com.peterscode.edutrack.grading.entity.AssessmentPaper;
import com.peterscode.edutrack.grading.entity.SubjectAssessment;
import com.peterscode.edutrack.grading.entity.TermGrade;
import com.peterscode.edutrack.grading.repository.AssessmentPaperRepository;
import com.peterscode.edutrack.grading.repository.SubjectAssessmentRepository;
import com.peterscode.edutrack.grading.repository.TermGradeRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.student.entity.Student;
import com.peterscode.edutrack.student.repository.StudentRepository;
import com.peterscode.edutrack.subject.entity.Subject;
import com.peterscode.edutrack.subject.repository.SubjectRepository;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.repository.StreamSubjectRepository;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExaminationService {

    private final ExamSeriesRepository examSeriesRepository;
    private final ExamRegistrationRepository examRegistrationRepository;
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final StaffRepository staffRepository;
    private final StreamRepository streamRepository;
    private final SubjectRepository subjectRepository;
    private final TermRepository termRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final AssessmentPaperRepository assessmentPaperRepository;
    private final SubjectAssessmentRepository subjectAssessmentRepository;
    private final TermGradeRepository termGradeRepository;

    // ==================== Exam Series ====================

    @Transactional
    public ExamSeriesView createExamSeries(Long schoolId, CreateExamSeriesRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        Term term = termRepository.findBySchoolIdAndId(schoolId, request.termId())
                .orElseThrow(() -> notFound("Term not found"));

        if (examSeriesRepository.existsBySchoolIdAndTermIdAndName(schoolId, term.getId(), request.name().trim())) {
            throw conflict("DUPLICATE_SERIES", "Exam series with this name already exists for the term");
        }

        validateDates(request);

        ExamSeries series = ExamSeries.builder()
                .schoolId(schoolId)
                .termId(term.getId())
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .registrationOpensOn(request.registrationOpensOn())
                .registrationClosesOn(request.registrationClosesOn())
                .status(ExamSeries.SeriesStatus.DRAFT)
                .createdBy(principal.getId())
                .build();
        series = examSeriesRepository.save(series);
        return toSeriesView(series, term.getName());
    }

    @Transactional(readOnly = true)
    public List<ExamSeriesView> examSeries(Long schoolId, UserPrincipal principal) {
        List<ExamSeries> series = examSeriesRepository.findBySchoolId(schoolId);
        Map<Long, String> termNames = new HashMap<>();
        return series.stream().map(s -> {
            String termName = termNames.computeIfAbsent(s.getTermId(), id ->
                    termRepository.findBySchoolIdAndId(schoolId, id).map(Term::getName).orElse(null));
            return toSeriesView(s, termName);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExamSeriesView getExamSeries(Long schoolId, Long id, UserPrincipal principal) {
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Exam series not found"));
        Term term = termRepository.findBySchoolIdAndId(schoolId, series.getTermId()).orElse(null);
        return toSeriesView(series, term != null ? term.getName() : null);
    }

    @Transactional
    public ExamSeriesView updateExamSeries(Long schoolId, Long id, UpdateExamSeriesRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Exam series not found"));
        if (series.getStatus() != ExamSeries.SeriesStatus.DRAFT) {
            throw forbidden("SERIES_NOT_DRAFT", "Only draft series can be updated");
        }
        Term term = termRepository.findBySchoolIdAndId(schoolId, request.termId())
                .orElseThrow(() -> notFound("Term not found"));
        if (!series.getTermId().equals(term.getId())
                && examSeriesRepository.existsBySchoolIdAndTermIdAndName(schoolId, term.getId(), request.name().trim())) {
            throw conflict("DUPLICATE_SERIES", "Exam series with this name already exists for the term");
        }
        validateDates(request);
        series.setName(request.name().trim());
        series.setTermId(term.getId());
        series.setStartDate(request.startDate());
        series.setEndDate(request.endDate());
        series.setRegistrationOpensOn(request.registrationOpensOn());
        series.setRegistrationClosesOn(request.registrationClosesOn());
        series = examSeriesRepository.save(series);
        return toSeriesView(series, term.getName());
    }

    @Transactional
    public ExamSeriesView updateStatus(Long schoolId, Long id, UpdateStatusRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Exam series not found"));
        series.setStatus(request.status());
        series = examSeriesRepository.save(series);
        Term term = termRepository.findBySchoolIdAndId(schoolId, series.getTermId()).orElse(null);
        return toSeriesView(series, term != null ? term.getName() : null);
    }

    // ==================== Registration ====================

    @Transactional
    public ExamRegistrationView registerStudent(Long schoolId, Long seriesId, RegisterStudentRequest request, UserPrincipal principal) {
        ExamSeries series = requireSeriesOpen(schoolId, seriesId);
        Student student = studentRepository.findBySchoolIdAndId(schoolId, request.studentId())
                .orElseThrow(() -> notFound("Student not found"));
        validateEligibleStudent(student, series);

        AssessmentPaper paper = assessmentPaperRepository.findById(request.assessmentPaperId())
                .orElseThrow(() -> notFound("Assessment paper not found"));
        validatePaperConfigured(schoolId, series, request.subjectId(), paper);

        // Check duplicate
        if (examRegistrationRepository
                .findBySchoolIdAndExamSeriesIdAndStudentIdAndAssessmentPaperId(
                        schoolId, seriesId, request.studentId(), request.assessmentPaperId()).isPresent()) {
            throw conflict("DUPLICATE_REGISTRATION", "Student already registered for this paper");
        }

        // R4: Check term not closed
        checkTermNotClosed(schoolId, series.getTermId());

        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, student.getStreamId()).orElse(null);

        ExamRegistration registration = ExamRegistration.builder()
                .schoolId(schoolId)
                .examSeriesId(seriesId)
                .studentId(request.studentId())
                .subjectId(request.subjectId())
                .assessmentPaperId(request.assessmentPaperId())
                .candidateNumber(generateCandidateNumber(schoolId, seriesId))
                .classLevelId(stream != null ? stream.getClassLevelId() : student.getClassLevelId())
                .streamId(student.getStreamId())
                .status(ExamRegistration.RegistrationStatus.REGISTERED)
                .registeredBy(principal.getId())
                .build();
        registration = examRegistrationRepository.save(registration);
        return toRegistrationView(registration, student.getFullName(), series.getName());
    }

    @Transactional
    public BulkRegisterResult bulkRegister(Long schoolId, Long seriesId, BulkRegisterRequest request, UserPrincipal principal) {
        ExamSeries series = requireSeriesOpen(schoolId, seriesId);
        checkTermNotClosed(schoolId, series.getTermId());

        // Get students
        List<Student> students;
        if (request.streamId() != null) {
            students = studentRepository.findBySchoolIdAndStreamIdAndActiveTrueOrderByFirstNameAscLastNameAsc(
                    schoolId, request.streamId());
        } else if (request.classLevelId() != null) {
            students = studentRepository.findBySchoolIdAndGradeLevel(schoolId, request.classLevelId());
        } else {
            throw new ExaminationRuleException("Stream ID or class level ID is required", "MISSING_STREAM", HttpStatus.BAD_REQUEST);
        }

        int created = 0;
        int skipped = 0;
        List<ExamRegistration> toSave = new ArrayList<>();

        for (Student student : students) {
            if (!student.isEnrolled()) continue;

            for (Long paperId : request.assessmentPaperIds()) {
                AssessmentPaper paper = assessmentPaperRepository.findById(paperId).orElse(null);
                if (paper == null) continue;

                Long subjectId = paper.getAssessmentTypeId();
                // Check if already registered
                if (examRegistrationRepository
                        .findBySchoolIdAndExamSeriesIdAndStudentIdAndAssessmentPaperId(
                                schoolId, seriesId, student.getId(), paperId).isPresent()) {
                    skipped++;
                    continue;
                }

                Stream stream = streamRepository.findBySchoolIdAndId(schoolId, student.getStreamId()).orElse(null);
                ExamRegistration reg = ExamRegistration.builder()
                        .schoolId(schoolId)
                        .examSeriesId(seriesId)
                        .studentId(student.getId())
                        .subjectId(subjectId)
                        .assessmentPaperId(paperId)
                        .candidateNumber(generateCandidateNumber(schoolId, seriesId))
                        .classLevelId(stream != null ? stream.getClassLevelId() : student.getClassLevelId())
                        .streamId(student.getStreamId())
                        .status(ExamRegistration.RegistrationStatus.REGISTERED)
                        .registeredBy(principal.getId())
                        .build();
                toSave.add(reg);
                created++;
            }
        }

        if (!toSave.isEmpty()) {
            examRegistrationRepository.saveAll(toSave);
        }
        return new BulkRegisterResult(created, skipped);
    }

    @Transactional
    public ExamRegistrationView withdrawRegistration(Long schoolId, Long id, WithdrawRequest request, UserPrincipal principal) {
        ExamRegistration reg = examRegistrationRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Registration not found"));
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, reg.getExamSeriesId()).orElse(null);
        if (series != null && series.getStatus() == ExamSeries.SeriesStatus.REGISTRATION_CLOSED) {
            requireAdmin(principal);
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ExaminationRuleException("Reason is required after registration closes", "REASON_REQUIRED", HttpStatus.BAD_REQUEST);
            }
        }
        reg.setStatus(ExamRegistration.RegistrationStatus.WITHDRAWN);
        reg.setReason(request.reason());
        reg = examRegistrationRepository.save(reg);
        Student student = studentRepository.findBySchoolIdAndId(schoolId, reg.getStudentId()).orElse(null);
        return toRegistrationView(reg, student != null ? student.getFullName() : null,
                series != null ? series.getName() : null);
    }

    @Transactional
    public ExamRegistrationView debarRegistration(Long schoolId, Long id, DebarRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        ExamRegistration reg = examRegistrationRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> notFound("Registration not found"));
        reg.setStatus(ExamRegistration.RegistrationStatus.DEBARRED);
        reg.setReason(request.reason());
        reg = examRegistrationRepository.save(reg);
        Student student = studentRepository.findBySchoolIdAndId(schoolId, reg.getStudentId()).orElse(null);
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, reg.getExamSeriesId()).orElse(null);
        return toRegistrationView(reg, student != null ? student.getFullName() : null,
                series != null ? series.getName() : null);
    }

    // ==================== Views ====================

    @Transactional(readOnly = true)
    public List<ExamRegistrationView> seriesRegistrations(Long schoolId, Long seriesId, UserPrincipal principal) {
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, seriesId)
                .orElseThrow(() -> notFound("Exam series not found"));

        List<ExamRegistration> registrations;
        if (principal.getRole() == Role.STUDENT) {
            registrations = examRegistrationRepository
                    .findBySchoolIdAndExamSeriesIdAndStudentId(schoolId, seriesId, principal.getId());
        } else if (principal.getRole() == Role.TEACHER) {
            // Teacher sees only assigned subjects
            Staff staff = staffRepository.findBySchoolIdAndUserId(schoolId, principal.getId()).orElse(null);
            if (staff == null) {
                return List.of();
            }
            registrations = examRegistrationRepository.findBySchoolIdAndExamSeriesId(schoolId, seriesId)
                    .stream()
                    .filter(r -> streamSubjectRepository
                            .existsBySchoolIdAndTeacherIdAndStreamIdAndSubjectIdAndAcademicYearId(
                                    schoolId, staff.getId(), r.getStreamId(), r.getSubjectId(),
                                    termRepository.findBySchoolIdAndId(schoolId, series.getTermId())
                                            .map(Term::getAcademicYearId).orElse(null)))
                    .collect(Collectors.toList());
        } else {
            registrations = examRegistrationRepository.findBySchoolIdAndExamSeriesId(schoolId, seriesId);
        }

        Map<Long, String> studentNames = new HashMap<>();
        return registrations.stream().map(r -> {
            String name = studentNames.computeIfAbsent(r.getStudentId(), sid ->
                    studentRepository.findBySchoolIdAndId(schoolId, sid).map(Student::getFullName).orElse(null));
            return toRegistrationView(r, name, series.getName());
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MyRegistrationView> myRegistrations(Long schoolId, UserPrincipal principal) {
        requireStudent(principal);
        List<ExamRegistration> registrations = new ArrayList<>();
        for (ExamSeries series : examSeriesRepository.findBySchoolId(schoolId)) {
            registrations.addAll(examRegistrationRepository
                    .findBySchoolIdAndExamSeriesIdAndStudentId(schoolId, series.getId(), principal.getId()));
        }
        Map<Long, String> seriesNames = new HashMap<>();
        return registrations.stream().map(r -> {
            String seriesName = seriesNames.computeIfAbsent(r.getExamSeriesId(), sid ->
                    examSeriesRepository.findBySchoolIdAndId(schoolId, sid).map(ExamSeries::getName).orElse(null));
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, r.getSubjectId()).orElse(null);
            return new MyRegistrationView(
                    r.getId(),
                    seriesName,
                    subject != null ? subject.getName() : null,
                    r.getCandidateNumber(),
                    r.getStatus().name()
            );
        }).collect(Collectors.toList());
    }

    // ==================== Helpers ====================

    private ExamSeries requireSeriesOpen(Long schoolId, Long seriesId) {
        ExamSeries series = examSeriesRepository.findBySchoolIdAndId(schoolId, seriesId)
                .orElseThrow(() -> notFound("Exam series not found"));
        if (series.getStatus() != ExamSeries.SeriesStatus.REGISTRATION_OPEN) {
            throw new ExaminationRuleException("Registration is not open", "REGISTRATION_NOT_OPEN", HttpStatus.CONFLICT);
        }
        return series;
    }

    private void validateEligibleStudent(Student student, ExamSeries series) {
        if (!student.isEnrolled()) {
            throw new ExaminationRuleException("Student is not active", "STUDENT_NOT_ACTIVE", HttpStatus.CONFLICT);
        }
        if (student.getAdmissionDate() != null && student.getAdmissionDate().isAfter(series.getEndDate())) {
            throw new ExaminationRuleException("Student admitted after series end date", "STUDENT_NOT_ADMITTED", HttpStatus.CONFLICT);
        }
        if (student.getStreamId() == null) {
            throw new ExaminationRuleException("Student has no stream", "STUDENT_NO_STREAM", HttpStatus.CONFLICT);
        }
    }

    private void validatePaperConfigured(Long schoolId, ExamSeries series, Long subjectId, AssessmentPaper paper) {
        SubjectAssessment sa = subjectAssessmentRepository
                .findBySchoolIdAndTermIdAndSubjectIdAndAssessmentTypeId(
                        schoolId, series.getTermId(), subjectId, paper.getAssessmentTypeId()).orElse(null);
        if (sa == null || !Boolean.TRUE.equals(sa.getActive())) {
            throw new ExaminationRuleException("Paper not configured for this subject", "PAPER_NOT_CONFIGURED", HttpStatus.CONFLICT);
        }
    }

    private void checkTermNotClosed(Long schoolId, Long termId) {
        List<TermGrade> existing = termGradeRepository.findBySchoolIdAndTermId(schoolId, termId);
        if (!existing.isEmpty()) {
            throw new ExaminationRuleException("Term is already closed", "TERM_CLOSED", HttpStatus.CONFLICT);
        }
    }

    private String generateCandidateNumber(Long schoolId, Long seriesId) {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "EXM-" + uuid;
    }

    private void validateDates(CreateExamSeriesRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new ExaminationRuleException("Start date must be before end date", "INVALID_DATES", HttpStatus.BAD_REQUEST);
        }
        if (request.registrationOpensOn().isAfter(request.registrationClosesOn())) {
            throw new ExaminationRuleException("Registration opens on must be before closes on", "INVALID_DATES", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDates(UpdateExamSeriesRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            throw new ExaminationRuleException("Start date must be before end date", "INVALID_DATES", HttpStatus.BAD_REQUEST);
        }
        if (request.registrationOpensOn().isAfter(request.registrationClosesOn())) {
            throw new ExaminationRuleException("Registration opens on must be before closes on", "INVALID_DATES", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SCHOOL_ADMIN && principal.getRole() != Role.SUPER_ADMIN) {
            throw new ExaminationRuleException("Admin access required", "ACCESS_DENIED", HttpStatus.FORBIDDEN);
        }
    }

    private void requireStudent(UserPrincipal principal) {
        if (principal.getRole() != Role.STUDENT) {
            throw new ExaminationRuleException("Student access required", "ACCESS_DENIED", HttpStatus.FORBIDDEN);
        }
    }

    private ExaminationRuleException notFound(String message) {
        return new ExaminationRuleException(message, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    private ExaminationRuleException conflict(String errorCode, String message) {
        return new ExaminationRuleException(message, errorCode, HttpStatus.CONFLICT);
    }

    private ExaminationRuleException forbidden(String errorCode, String message) {
        return new ExaminationRuleException(message, errorCode, HttpStatus.FORBIDDEN);
    }

    private ExamSeriesView toSeriesView(ExamSeries series, String termName) {
        return new ExamSeriesView(
                series.getId(), series.getName(), termName, series.getTermId(),
                series.getStartDate(), series.getEndDate(),
                series.getRegistrationOpensOn(), series.getRegistrationClosesOn(),
                series.getStatus().name()
        );
    }

    private ExamRegistrationView toRegistrationView(ExamRegistration reg, String studentName, String seriesName) {
        Subject subject = subjectRepository.findById(reg.getSubjectId()).orElse(null);
        return new ExamRegistrationView(
                reg.getId(), seriesName, reg.getStudentId(), studentName,
                subject != null ? subject.getName() : null,
                reg.getCandidateNumber(), reg.getStatus().name(), reg.getReason()
        );
    }
}
