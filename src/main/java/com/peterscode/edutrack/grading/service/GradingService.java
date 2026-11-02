package com.peterscode.edutrack.grading.service;

import com.peterscode.edutrack.academicyear.entity.AcademicYear;
import com.peterscode.edutrack.academicyear.repository.AcademicYearRepository;
import com.peterscode.edutrack.classlevel.entity.Stream;
import com.peterscode.edutrack.classlevel.repository.StreamRepository;
import com.peterscode.edutrack.grading.dto.GradingRequests;
import com.peterscode.edutrack.grading.dto.GradingViews;
import com.peterscode.edutrack.grading.entity.*;
import com.peterscode.edutrack.grading.exception.GradingEx;
import com.peterscode.edutrack.grading.repository.*;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.guardian.repository.ParentStudentMappingRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.ParentAccessGuard;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradingService {
    private final SchoolRepository schoolRepository;
    private final AcademicYearRepository academicYearRepository;
    private final StreamRepository streamRepository;
    private final SubjectRepository subjectRepository;
    private final StaffRepository staffRepository;
    private final StudentRepository studentRepository;
    private final TermRepository termRepository;
    private final StreamSubjectRepository streamSubjectRepository;
    private final GradeScaleRepository gradeScaleRepository;
    private final GradeScaleRangeRepository gradeScaleRangeRepository;
    private final AssessmentTypeRepository assessmentTypeRepository;
    private final AssessmentPaperRepository assessmentPaperRepository;
    private final SubjectAssessmentRepository subjectAssessmentRepository;
    private final StudentScoreRepository studentScoreRepository;
    private final TermGradeRepository termGradeRepository;
    private final GuardianRepository guardianRepository;
    private final ParentStudentMappingRepository parentStudentMappingRepository;
    private final Clock clock;

    // ==================== Grade Scale ====================

    @Transactional
    @CacheEvict(value = "gradescales", key = "#schoolId")
    public GradingViews.GradeScaleView createGradeScale(Long schoolId, GradingRequests.CreateGradeScaleRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        AcademicYear year = academicYearRepository.findBySchoolIdAndId(schoolId, request.academicYearId())
                .orElseThrow(() -> notFound("Academic year not found"));
        validateRanges(request.ranges());
        GradeScale scale = GradeScale.builder()
                .schoolId(schoolId)
                .academicYearId(year.getId())
                .name(request.name().trim())
                .active(true)
                .build();
        List<GradeScaleRange> ranges = request.ranges().stream().map(r ->
                GradeScaleRange.builder()
                        .scaleId(scale.getId())
                        .letterGrade(r.letterGrade().trim().toUpperCase())
                        .gpa(r.gpa())
                        .minPercentage(r.minPercentage())
                        .maxPercentage(r.maxPercentage())
                        .description(r.description())
                        .sortOrder(r.sortOrder() != null ? r.sortOrder() : r.minPercentage().intValue())
                        .build()
        ).collect(Collectors.toList());
        scale.setRanges(ranges);
        GradeScale saved = gradeScaleRepository.save(scale);
        return gradeScaleView(saved, year.getName());
    }

    @Transactional
    @CacheEvict(value = "gradescales", key = "#schoolId")
    public GradingViews.GradeScaleView updateGradeScale(Long schoolId, Long scaleId, GradingRequests.CreateGradeScaleRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        GradeScale scale = gradeScaleRepository.findBySchoolIdAndId(schoolId, scaleId)
                .orElseThrow(() -> notFound("Grade scale not found"));
        validateRanges(request.ranges());
        scale.setName(request.name().trim());
        scale.setActive(true);
        scale.getRanges().clear();
        List<GradeScaleRange> ranges = request.ranges().stream().map(r ->
                GradeScaleRange.builder()
                        .scaleId(scale.getId())
                        .letterGrade(r.letterGrade().trim().toUpperCase())
                        .gpa(r.gpa())
                        .minPercentage(r.minPercentage())
                        .maxPercentage(r.maxPercentage())
                        .description(r.description())
                        .sortOrder(r.sortOrder() != null ? r.sortOrder() : r.minPercentage().intValue())
                        .build()
        ).collect(Collectors.toList());
        scale.getRanges().addAll(ranges);
        GradeScale saved = gradeScaleRepository.save(scale);
        AcademicYear year = academicYearRepository.findBySchoolIdAndId(schoolId, saved.getAcademicYearId()).orElse(null);
        return gradeScaleView(saved, year != null ? year.getName() : null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "gradescales", key = "#schoolId", unless = "#result == null")
    public List<GradingViews.GradeScaleView> gradeScales(Long schoolId, UserPrincipal principal) {
        List<GradeScale> scales = gradeScaleRepository.findBySchoolIdAndActiveTrue(schoolId);
        return scales.stream().map(s -> {
            AcademicYear year = academicYearRepository.findBySchoolIdAndId(schoolId, s.getAcademicYearId()).orElse(null);
            return gradeScaleView(s, year != null ? year.getName() : null);
        }).collect(Collectors.toList());
    }

    // ==================== Assessment Type ====================

    @Transactional
    @CacheEvict(value = "assessmenttypes", key = "#schoolId")
    public GradingViews.AssessmentTypeView createAssessmentType(Long schoolId, GradingRequests.CreateAssessmentTypeRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        int maxOrder = assessmentTypeRepository.findBySchoolIdAndActiveTrueOrderBySortOrder(schoolId).size();
        AssessmentType type = AssessmentType.builder()
                .schoolId(schoolId)
                .name(request.name().trim())
                .code(request.code().trim().toUpperCase())
                .sortOrder(maxOrder)
                .active(true)
                .build();
        AssessmentType saved = assessmentTypeRepository.save(type);
        return assessmentTypeView(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "assessmenttypes", key = "#schoolId", unless = "#result == null")
    public List<GradingViews.AssessmentTypeView> assessmentTypes(Long schoolId, UserPrincipal principal) {
        return assessmentTypeRepository.findBySchoolIdAndActiveTrueOrderBySortOrder(schoolId).stream()
                .map(this::assessmentTypeView).collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "assessmentpapers", key = "T(String).valueOf(#typeId + ':' + #schoolId)")
    public GradingViews.AssessmentPaperView addAssessmentPaper(Long schoolId, Long typeId, GradingRequests.CreateAssessmentPaperRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        AssessmentType type = assessmentTypeRepository.findBySchoolIdAndId(schoolId, typeId)
                .orElseThrow(() -> notFound("Assessment type not found"));
        int maxOrder = assessmentPaperRepository.findByAssessmentTypeIdOrderBySortOrder(type.getId()).size();
        AssessmentPaper paper = AssessmentPaper.builder()
                .assessmentTypeId(type.getId())
                .name(request.name().trim())
                .code(request.code().trim().toUpperCase())
                .weight(request.weight())
                .maxScore(request.maxScore())
                .sortOrder(maxOrder)
                .build();
        AssessmentPaper saved = assessmentPaperRepository.save(paper);
        return assessmentPaperView(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "assessmentpapers", key = "T(String).valueOf(#typeId + ':' + #schoolId)", unless = "#result == null")
    public List<GradingViews.AssessmentPaperView> assessmentPapers(Long schoolId, Long typeId, UserPrincipal principal) {
        AssessmentType type = assessmentTypeRepository.findBySchoolIdAndId(schoolId, typeId)
                .orElseThrow(() -> notFound("Assessment type not found"));
        return assessmentPaperRepository.findByAssessmentTypeIdOrderBySortOrder(type.getId()).stream()
                .map(this::assessmentPaperView).collect(Collectors.toList());
    }

    // ==================== Subject Assessment ====================

    @Transactional
    public void configureSubjectAssessments(Long schoolId, Long termId, GradingRequests.ConfigureSubjectAssessmentsRequest request, UserPrincipal principal) {
        requireAdmin(principal);
        Term term = termRepository.findBySchoolIdAndId(schoolId, termId)
                .orElseThrow(() -> notFound("Term not found"));
        Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, request.subjectId())
                .orElseThrow(() -> notFound("Subject not found"));
        for (Long assessmentTypeId : request.assessmentTypeIds()) {
            AssessmentType type = assessmentTypeRepository.findBySchoolIdAndId(schoolId, assessmentTypeId)
                    .orElseThrow(() -> notFound("Assessment type not found"));
            boolean exists = subjectAssessmentRepository.existsBySchoolIdAndTermIdAndSubjectIdAndAssessmentTypeId(
                    schoolId, termId, subject.getId(), assessmentTypeId);
            if (!exists) {
                SubjectAssessment sa = SubjectAssessment.builder()
                        .schoolId(schoolId)
                        .termId(termId)
                        .subjectId(subject.getId())
                        .assessmentTypeId(assessmentTypeId)
                        .active(true)
                        .build();
                subjectAssessmentRepository.save(sa);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<GradingViews.SubjectAssessmentView> subjectAssessments(Long schoolId, Long termId, Long subjectId, UserPrincipal principal) {
        Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, subjectId)
                .orElseThrow(() -> notFound("Subject not found"));
        List<SubjectAssessment> assessments = subjectAssessmentRepository.findBySchoolIdAndTermIdAndSubjectId(schoolId, termId, subjectId);
        return assessments.stream().map(sa -> {
            AssessmentType type = assessmentTypeRepository.findById(sa.getAssessmentTypeId()).orElse(null);
            List<GradingViews.AssessmentPaperView> papers = List.of();
            if (type != null) {
                papers = assessmentPaperRepository.findByAssessmentTypeIdOrderBySortOrder(type.getId()).stream()
                        .map(this::assessmentPaperView).collect(Collectors.toList());
            }
            return new GradingViews.SubjectAssessmentView(
                    subject.getId(), subject.getName(),
                    type != null ? type.getId() : null,
                    type != null ? type.getName() : null,
                    papers
            );
        }).collect(Collectors.toList());
    }

    // ==================== Score Entry ====================

    @Transactional
    public GradingViews.SubmitScoresResponse submitScores(Long schoolId, GradingRequests.SubmitScoresRequest request, UserPrincipal principal) {
        requireTeacherOrAdmin(principal);
        Term term = termRepository.findBySchoolIdAndId(schoolId, request.termId())
                .orElseThrow(() -> notFound("Term not found"));
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, request.streamId())
                .orElseThrow(() -> notFound("Stream not found"));
        Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, request.subjectId())
                .orElseThrow(() -> notFound("Subject not found"));
        validateTeacherAssignment(schoolId, principal, stream.getId(), subject.getId(), term.getId());
        checkTermNotClosed(schoolId, term.getId());
        BigDecimal paperMaxScore = BigDecimal.ZERO;
        if (request.assessmentPaperId() != null) {
            AssessmentPaper paper = assessmentPaperRepository.findById(request.assessmentPaperId()).orElse(null);
            if (paper == null) throw notFound("Assessment paper not found");
            paperMaxScore = paper.getMaxScore();
        }
        List<StudentScore> scores = new ArrayList<>();
        int submittedCount = 0;
        int updatedCount = 0;
        for (GradingRequests.ScoreEntry entry : request.entries()) {
            Student student = studentRepository.findBySchoolIdAndId(schoolId, entry.studentId())
                    .orElseThrow(() -> badRule("STUDENT_NOT_IN_STREAM", "Student not found"));
            if (!student.getStreamId().equals(stream.getId())) {
                throw badRule("STUDENT_NOT_IN_STREAM", "Student not in stream");
            }
            BigDecimal maxScore = request.assessmentPaperId() != null ? paperMaxScore : BigDecimal.valueOf(100);
            if (entry.score().compareTo(maxScore) > 0) {
                throw badRule("SCORE_EXCEEDS_MAX", "Score exceeds max score of " + maxScore);
            }
            Optional<StudentScore> existing = studentScoreRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectIdAndAssessmentPaperId(
                    schoolId, request.termId(), stream.getId(), subject.getId(), request.assessmentPaperId())
                    .stream().filter(s -> s.getStudentId().equals(entry.studentId())).findFirst();
            if (existing.isPresent()) {
                StudentScore s = existing.get();
                s.setScore(entry.score());
                s.setRemarks(entry.remarks());
                updatedCount++;
                scores.add(s);
            } else {
                StudentScore s = StudentScore.builder()
                        .schoolId(schoolId)
                        .termId(request.termId())
                        .streamId(stream.getId())
                        .subjectId(subject.getId())
                        .studentId(entry.studentId())
                        .assessmentPaperId(request.assessmentPaperId())
                        .score(entry.score())
                        .maxScore(maxScore)
                        .remarks(entry.remarks())
                        .enteredBy(principal.getId())
                        .build();
                scores.add(s);
                submittedCount++;
            }
        }
        List<StudentScore> saved = studentScoreRepository.saveAllAndFlush(scores);
        List<GradingViews.StudentScoreView> views = saved.stream().map(s -> {
            Student student = studentRepository.findBySchoolIdAndId(schoolId, s.getStudentId()).orElse(null);
            String paperName = null;
            if (s.getAssessmentPaperId() != null) {
                AssessmentPaper paper = assessmentPaperRepository.findById(s.getAssessmentPaperId()).orElse(null);
                if (paper != null) paperName = paper.getName();
            }
            return new GradingViews.StudentScoreView(
                    s.getStudentId(),
                    student != null ? student.getFullName() : null,
                    paperName,
                    s.getScore(),
                    s.getMaxScore(),
                    s.getRemarks(),
                    s.getCreatedAt()
            );
        }).collect(Collectors.toList());
        return new GradingViews.SubmitScoresResponse(submittedCount, updatedCount, views);
    }

    @Transactional(readOnly = true)
    public List<GradingViews.StudentScoreView> streamScores(Long schoolId, Long termId, Long streamId, Long subjectId, UserPrincipal principal) {
        requireTeacherOrAdmin(principal);
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, streamId)
                .orElseThrow(() -> notFound("Stream not found"));
        Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, subjectId)
                .orElseThrow(() -> notFound("Subject not found"));
        List<StudentScore> scores = studentScoreRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(schoolId, termId, streamId, subjectId);
        return scores.stream().map(s -> {
            Student student = studentRepository.findBySchoolIdAndId(schoolId, s.getStudentId()).orElse(null);
            String paperName = null;
            if (s.getAssessmentPaperId() != null) {
                AssessmentPaper paper = assessmentPaperRepository.findById(s.getAssessmentPaperId()).orElse(null);
                if (paper != null) paperName = paper.getName();
            }
            return new GradingViews.StudentScoreView(
                    s.getStudentId(),
                    student != null ? student.getFullName() : null,
                    paperName,
                    s.getScore(),
                    s.getMaxScore(),
                    s.getRemarks(),
                    s.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }

    // ==================== Student Self-Service ====================

    @Transactional(readOnly = true)
    public List<GradingViews.MyScoreView> myScores(Long schoolId, Long studentId, Long termId, UserPrincipal principal) {
        requireStudent(principal, studentId);
        List<StudentScore> scores = studentScoreRepository.findBySchoolIdAndStudentIdAndTermId(schoolId, studentId, termId);
        return scores.stream().map(s -> {
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, s.getSubjectId()).orElse(null);
            String paperName = null;
            String typeName = null;
            if (s.getAssessmentPaperId() != null) {
                AssessmentPaper paper = assessmentPaperRepository.findById(s.getAssessmentPaperId()).orElse(null);
                if (paper != null) {
                    paperName = paper.getName();
                    AssessmentType type = assessmentTypeRepository.findById(paper.getAssessmentTypeId()).orElse(null);
                    if (type != null) typeName = type.getName();
                }
            }
            return new GradingViews.MyScoreView(
                    subject != null ? subject.getName() : null,
                    typeName,
                    paperName,
                    s.getScore(),
                    s.getMaxScore()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GradingViews.MyTermGradeView> myTermGrades(Long schoolId, Long studentId, Long termId, UserPrincipal principal) {
        requireStudent(principal, studentId);
        List<TermGrade> grades = termGradeRepository.findBySchoolIdAndStudentIdAndTermId(schoolId, studentId, termId);
        return grades.stream().map(g -> {
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, g.getSubjectId()).orElse(null);
            return new GradingViews.MyTermGradeView(
                    subject != null ? subject.getName() : null,
                    g.getTotalRawScore(),
                    g.getTotalMaxScore(),
                    g.getWeightedPercentage(),
                    g.getLetterGrade(),
                    g.getGpa(),
                    g.getStreamRank()
            );
        }).collect(Collectors.toList());
    }

    // ==================== Term Close ====================

    @Transactional
    public void closeTerm(Long schoolId, Long termId, UserPrincipal principal) {
        requireAdmin(principal);
        Term term = termRepository.findBySchoolIdAndId(schoolId, termId)
                .orElseThrow(() -> notFound("Term not found"));
        List<TermGrade> existing = termGradeRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(
                schoolId, termId, null, null);
        if (!termGradeRepository.findBySchoolIdAndStudentIdAndTermId(schoolId, null, termId).isEmpty()) {
            throw conflict("TERM_CLOSED", "Term grades already computed");
        }
        GradeScale scale = gradeScaleRepository.findBySchoolIdAndAcademicYearIdAndActiveTrue(schoolId, term.getAcademicYearId())
                .stream().findFirst()
                .orElseThrow(() -> notFound("GRADE_SCALE_NOT_FOUND", "No active grade scale for this academic year"));
        List<SubjectAssessment> subjectAssessments = subjectAssessmentRepository.findBySchoolIdAndTermId(schoolId, termId);
        Set<Long> streamIds = new HashSet<>();
        for (SubjectAssessment sa : subjectAssessments) {
            List<StudentScore> scores = studentScoreRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(
                    schoolId, termId, null, sa.getSubjectId());
            for (StudentScore s : scores) {
                streamIds.add(s.getStreamId());
            }
        }
        for (Long streamId : streamIds) {
            computeTermGrades(schoolId, term, scale, streamId, principal.getId());
        }
    }

    private void computeTermGrades(Long schoolId, Term term, GradeScale scale, Long streamId, Long computedBy) {
        List<SubjectAssessment> subjectAssessments = subjectAssessmentRepository.findBySchoolIdAndTermId(schoolId, term.getId());
        Map<Long, List<StudentScore>> scoresBySubject = new HashMap<>();
        for (SubjectAssessment sa : subjectAssessments) {
            List<StudentScore> scores = studentScoreRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(
                    schoolId, term.getId(), streamId, sa.getSubjectId());
            scoresBySubject.put(sa.getSubjectId(), scores);
        }
        Set<Long> studentIds = scoresBySubject.values().stream()
                .flatMap(List::stream)
                .map(StudentScore::getStudentId)
                .collect(Collectors.toSet());
        for (Long subjectId : scoresBySubject.keySet()) {
            List<StudentScore> scores = scoresBySubject.get(subjectId);
            Map<Long, List<StudentScore>> byStudent = scores.stream()
                    .collect(Collectors.groupingBy(StudentScore::getStudentId));
            List<TermGrade> grades = new ArrayList<>();
            for (Long studentId : byStudent.keySet()) {
                List<StudentScore> studentScores = byStudent.get(studentId);
                BigDecimal totalRaw = BigDecimal.ZERO;
                BigDecimal totalMax = BigDecimal.ZERO;
                for (StudentScore s : studentScores) {
                    totalRaw = totalRaw.add(s.getScore());
                    totalMax = totalMax.add(s.getMaxScore());
                }
                BigDecimal percentage = totalMax.compareTo(BigDecimal.ZERO) > 0
                        ? totalRaw.multiply(BigDecimal.valueOf(100)).divide(totalMax, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                GradeScaleRange matchedRange = findMatchingRange(scale, percentage);
                grades.add(TermGrade.builder()
                        .schoolId(schoolId)
                        .termId(term.getId())
                        .streamId(streamId)
                        .subjectId(subjectId)
                        .studentId(studentId)
                        .totalRawScore(totalRaw)
                        .totalMaxScore(totalMax)
                        .weightedPercentage(percentage)
                        .letterGrade(matchedRange != null ? matchedRange.getLetterGrade() : "N/A")
                        .gpa(matchedRange != null ? matchedRange.getGpa() : BigDecimal.ZERO)
                        .streamRank(0)
                        .computedAt(LocalDateTime.now(clock))
                        .computedBy(computedBy)
                        .build());
            }
            grades.sort((a, b) -> b.getWeightedPercentage().compareTo(a.getWeightedPercentage()));
            for (int i = 0; i < grades.size(); i++) {
                grades.get(i).setStreamRank(i + 1);
            }
            termGradeRepository.saveAllAndFlush(grades);
        }
    }

    @Transactional(readOnly = true)
    public List<GradingViews.TermGradeView> streamTermGrades(Long schoolId, Long termId, Long streamId, Long subjectId, UserPrincipal principal) {
        requireTeacherOrAdmin(principal);
        List<TermGrade> grades = termGradeRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(schoolId, termId, streamId, subjectId);
        return grades.stream().map(g -> {
            Student student = studentRepository.findBySchoolIdAndId(schoolId, g.getStudentId()).orElse(null);
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, g.getSubjectId()).orElse(null);
            return new GradingViews.TermGradeView(
                    g.getStudentId(),
                    student != null ? student.getFullName() : null,
                    subject != null ? subject.getName() : null,
                    g.getTotalRawScore(),
                    g.getTotalMaxScore(),
                    g.getWeightedPercentage(),
                    g.getLetterGrade(),
                    g.getGpa(),
                    g.getStreamRank()
            );
        }).collect(Collectors.toList());
    }

    // ==================== Statistics ====================

    @Transactional(readOnly = true)
    public GradingViews.ClassStatisticsView classStatistics(Long schoolId, Long termId, Long streamId, Long subjectId, UserPrincipal principal) {
        requireTeacherOrAdmin(principal);
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, streamId)
                .orElseThrow(() -> notFound("Stream not found"));
        Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, subjectId)
                .orElseThrow(() -> notFound("Subject not found"));
        List<TermGrade> grades = termGradeRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(schoolId, termId, streamId, subjectId);
        if (grades.isEmpty()) {
            return new GradingViews.ClassStatisticsView(
                    subject.getName(), stream.getName(), 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
            );
        }
        List<BigDecimal> percentages = grades.stream().map(TermGrade::getWeightedPercentage).sorted().collect(Collectors.toList());
        BigDecimal sum = percentages.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(percentages.size()), 2, RoundingMode.HALF_UP);
        BigDecimal median;
        int mid = percentages.size() / 2;
        if (percentages.size() % 2 == 0) {
            median = percentages.get(mid - 1).add(percentages.get(mid)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        } else {
            median = percentages.get(mid);
        }
        long passCount = grades.stream().filter(g -> g.getWeightedPercentage().compareTo(BigDecimal.valueOf(50)) >= 0).count();
        BigDecimal passRate = BigDecimal.valueOf(passCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(grades.size()), 2, RoundingMode.HALF_UP);
        return new GradingViews.ClassStatisticsView(
                subject.getName(), stream.getName(), grades.size(),
                average, median,
                percentages.get(percentages.size() - 1),
                percentages.get(0),
                passRate
        );
    }

    @Transactional(readOnly = true)
    public List<GradingViews.TermGradeView> studentTermGrades(Long schoolId, Long termId, Long studentId, UserPrincipal principal) {
        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> notFound("Student not found"));
        // Authorization matrix: PARENT via ParentAccessGuard, STUDENT for self, TEACHER per assignment, ADMIN for all
        if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(
                    schoolId, principal, parentStudentMappingRepository, guardianRepository, studentId);
        } else if (principal.getRole() == Role.STUDENT) {
            requireStudent(principal, studentId);
        } else if (principal.getRole() == Role.TEACHER) {
            // Teacher can only see term grades for assigned subjects
            termRepository.findBySchoolIdAndId(schoolId, termId)
                    .orElseThrow(() -> notFound("Term not found"));
        }
        // SCHOOL_ADMIN and SUPER_ADMIN have full access
        List<TermGrade> grades = termGradeRepository.findBySchoolIdAndStudentIdAndTermId(schoolId, studentId, termId);
        return grades.stream().map(g -> {
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, g.getSubjectId()).orElse(null);
            return new GradingViews.TermGradeView(
                    g.getStudentId(),
                    student.getFullName(),
                    subject != null ? subject.getName() : null,
                    g.getTotalRawScore(),
                    g.getTotalMaxScore(),
                    g.getWeightedPercentage(),
                    g.getLetterGrade(),
                    g.getGpa(),
                    g.getStreamRank()
            );
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GradingViews.ReportCardView reportCard(Long schoolId, Long termId, Long studentId, UserPrincipal principal) {
        Student student = studentRepository.findBySchoolIdAndId(schoolId, studentId)
                .orElseThrow(() -> notFound("Student not found"));
        requireStudentOrParent(schoolId, principal, studentId);
        Term term = termRepository.findBySchoolIdAndId(schoolId, termId)
                .orElseThrow(() -> notFound("Term not found"));
        Stream stream = streamRepository.findBySchoolIdAndId(schoolId, student.getStreamId()).orElse(null);
        List<TermGrade> grades = termGradeRepository.findBySchoolIdAndStudentIdAndTermId(schoolId, studentId, termId);
        List<GradingViews.TermGradeView> gradeViews = grades.stream().map(g -> {
            Subject subject = subjectRepository.findBySchoolIdAndId(schoolId, g.getSubjectId()).orElse(null);
            return new GradingViews.TermGradeView(
                    g.getStudentId(),
                    student.getFullName(),
                    subject != null ? subject.getName() : null,
                    g.getTotalRawScore(),
                    g.getTotalMaxScore(),
                    g.getWeightedPercentage(),
                    g.getLetterGrade(),
                    g.getGpa(),
                    g.getStreamRank()
            );
        }).collect(Collectors.toList());
        BigDecimal overallGpa = grades.stream().map(TermGrade::getGpa).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!grades.isEmpty()) {
            overallGpa = overallGpa.divide(BigDecimal.valueOf(grades.size()), 2, RoundingMode.HALF_UP);
        }
        Integer overallRank = grades.stream().map(TermGrade::getStreamRank).min(Comparator.naturalOrder()).orElse(null);
        return new GradingViews.ReportCardView(
                student.getFullName(),
                stream != null ? stream.getName() : null,
                term.getName(),
                gradeViews,
                overallGpa,
                overallRank
        );
    }

    // ==================== Security Helpers ====================

    private Long schoolId(Long explicitSchoolId, UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            if (explicitSchoolId == null) throw notFound("School id is required for super admin");
            schoolRepository.findById(explicitSchoolId).orElseThrow(() -> notFound("School not found"));
            return explicitSchoolId;
        }
        Long schoolId = principal.getSchoolId();
        if (schoolId == null) throw forbidden("SCHOOL_REQUIRED", "School context is required");
        if (explicitSchoolId != null && !explicitSchoolId.equals(schoolId)) {
            throw forbidden("ACCESS_DENIED", "Access denied");
        }
        return schoolId;
    }

    private void requireAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SCHOOL_ADMIN && principal.getRole() != Role.SUPER_ADMIN) {
            throw forbidden("ACCESS_DENIED", "Admin access required");
        }
    }

    private void requireTeacherOrAdmin(UserPrincipal principal) {
        Role role = principal.getRole();
        if (role != Role.TEACHER && role != Role.SCHOOL_ADMIN && role != Role.SUPER_ADMIN) {
            throw forbidden("ACCESS_DENIED", "Teacher or admin access required");
        }
    }

    private void requireStudent(UserPrincipal principal, Long expectedStudentId) {
        if (principal.getRole() != Role.STUDENT) {
            throw forbidden("ACCESS_DENIED", "Student access required");
        }
        if (!principal.getId().equals(expectedStudentId)) {
            throw forbidden("ACCESS_DENIED", "You can only view your own scores");
        }
    }

    private void requireStudentOrParent(Long schoolId, UserPrincipal principal, Long studentId) {
        if (principal.getRole() == Role.STUDENT) {
            if (!principal.getId().equals(studentId)) {
                throw forbidden("ACCESS_DENIED", "You can only view your own report card");
            }
        } else if (principal.getRole() == Role.PARENT) {
            ParentAccessGuard.assertLinked(
                    schoolId, principal, parentStudentMappingRepository, guardianRepository, studentId);
        } else if (principal.getRole() != Role.SCHOOL_ADMIN && principal.getRole() != Role.SUPER_ADMIN) {
            throw forbidden("ACCESS_DENIED", "Student or parent access required");
        }
    }

    private void validateTeacherAssignment(Long schoolId, UserPrincipal principal, Long streamId, Long subjectId, Long termId) {
        if (principal.getRole() == Role.SCHOOL_ADMIN || principal.getRole() == Role.SUPER_ADMIN) return;
        Staff teacher = staffRepository.findBySchoolIdAndUserId(schoolId, principal.getId())
                .orElseThrow(() -> notFound("Teacher record not found"));
        Term term = termRepository.findBySchoolIdAndId(schoolId, termId)
                .orElseThrow(() -> notFound("Term not found"));
        boolean assigned = streamSubjectRepository.existsBySchoolIdAndTeacherIdAndStreamIdAndSubjectIdAndAcademicYearId(
                schoolId, teacher.getId(), streamId, subjectId, term.getAcademicYearId());
        if (!assigned) {
            throw forbidden("TEACHER_NOT_ASSIGNED", "Teacher not assigned to this subject/stream");
        }
    }

    private void checkTermNotClosed(Long schoolId, Long termId) {
        List<TermGrade> existing = termGradeRepository.findBySchoolIdAndTermIdAndStreamIdAndSubjectId(schoolId, termId, null, null);
        if (!existing.isEmpty()) {
            throw conflict("TERM_CLOSED", "Term is already closed. Scores cannot be modified.");
        }
    }

    // ==================== Validation ====================

    private void validateRanges(List<GradingRequests.CreateGradeScaleRequest.Range> ranges) {
        for (int i = 0; i < ranges.size(); i++) {
            GradingRequests.CreateGradeScaleRequest.Range r = ranges.get(i);
            if (r.maxPercentage().compareTo(r.minPercentage()) <= 0) {
                throw badRule("INVALID_RANGE", "Max percentage must be greater than min for range " + r.letterGrade());
            }
            for (int j = i + 1; j < ranges.size(); j++) {
                GradingRequests.CreateGradeScaleRequest.Range other = ranges.get(j);
                if (r.minPercentage().compareTo(other.maxPercentage()) < 0 && r.maxPercentage().compareTo(other.minPercentage()) > 0) {
                    throw badRule("RANGE_OVERLAP", "Ranges " + r.letterGrade() + " and " + other.letterGrade() + " overlap");
                }
            }
        }
    }

    private GradeScaleRange findMatchingRange(GradeScale scale, BigDecimal percentage) {
        return scale.getRanges().stream()
                .filter(r -> percentage.compareTo(r.getMinPercentage()) >= 0 && percentage.compareTo(r.getMaxPercentage()) <= 0)
                .findFirst()
                .orElse(scale.getRanges().stream().findFirst().orElse(null));
    }

    // ==================== View Mappers ====================

    private GradingViews.GradeScaleView gradeScaleView(GradeScale scale, String academicYearName) {
        List<GradingViews.GradeScaleRangeView> rangeViews = scale.getRanges().stream().map(r ->
                new GradingViews.GradeScaleRangeView(
                        r.getLetterGrade(), r.getGpa(),
                        r.getMinPercentage(), r.getMaxPercentage(),
                        r.getDescription()
                )
        ).collect(Collectors.toList());
        return new GradingViews.GradeScaleView(scale.getId(), scale.getName(), academicYearName, scale.getActive(), rangeViews);
    }

    private GradingViews.AssessmentTypeView assessmentTypeView(AssessmentType type) {
        List<GradingViews.AssessmentPaperView> paperViews = type.getPapers().stream()
                .map(this::assessmentPaperView).collect(Collectors.toList());
        return new GradingViews.AssessmentTypeView(type.getId(), type.getName(), type.getCode(), paperViews);
    }

    private GradingViews.AssessmentPaperView assessmentPaperView(AssessmentPaper paper) {
        return new GradingViews.AssessmentPaperView(paper.getId(), paper.getName(), paper.getCode(), paper.getWeight(), paper.getMaxScore());
    }

    // ==================== Exception Helpers ====================

    private GradingEx notFound(String message) {
        return new GradingEx("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, message);
    }

    private GradingEx notFound(String code, String message) {
        return new GradingEx(code, HttpStatus.NOT_FOUND, message);
    }

    private GradingEx badRule(String code, String message) {
        return new GradingEx(code, HttpStatus.BAD_REQUEST, message);
    }

    private GradingEx conflict(String code, String message) {
        return new GradingEx(code, HttpStatus.CONFLICT, message);
    }

    private GradingEx forbidden(String code, String message) {
        return new GradingEx(code, HttpStatus.FORBIDDEN, message);
    }
}
