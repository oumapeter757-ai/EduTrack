package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.fees.dto.CreateFeeStructureRequestDto;
import com.peterscode.edutrack.fees.dto.FeeStructureView;
import com.peterscode.edutrack.fees.entity.FeeItem;
import com.peterscode.edutrack.fees.entity.FeeStructure;
import com.peterscode.edutrack.fees.enums.FeeStructureStatus;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeeStructureRepository;
import com.peterscode.edutrack.fees.security.FeesAccessGuard;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.timetable.entity.Term;
import com.peterscode.edutrack.timetable.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class FeeStructureService {

    private final FeeStructureRepository feeStructureRepository;
    private final TermRepository termRepository;

    @Transactional
    public FeeStructureView create(UserPrincipal principal, CreateFeeStructureRequestDto request) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();

        if (feeStructureRepository.existsBySchoolIdAndTermIdAndClassLevelIdAndName(
                schoolId, request.termId(), request.classLevelId(), request.name())) {
            throw new FeesRuleException("DUPLICATE_FEE_STRUCTURE", CONFLICT,
                    "A fee structure with this name already exists for the given term and class");
        }

        Term term = termRepository.findBySchoolIdAndId(schoolId, request.termId())
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Term not found"));

        List<FeeItem> items = new ArrayList<>();
        for (CreateFeeStructureRequestDto.CreateFeeItemRequestDto itemReq : request.items()) {
            FeeItem item = FeeItem.builder()
                    .structureId(null) // will be set after structure is saved
                    .name(itemReq.name())
                    .amount(itemReq.amount())
                    .mandatory(itemReq.mandatory())
                    .build();
            items.add(item);
        }

        FeeStructure structure = FeeStructure.builder()
                .schoolId(schoolId)
                .academicYearId(term.getAcademicYearId())
                .termId(request.termId())
                .classLevelId(request.classLevelId())
                .name(request.name())
                .items(items)
                .build();

        structure = feeStructureRepository.save(structure);

        // Update structureId on items after save
        for (FeeItem item : items) {
            item.setStructureId(structure.getId());
        }

        return toView(structure);
    }

    @Transactional(readOnly = true)
    public Page<FeeStructureView> list(UserPrincipal principal, Pageable pageable) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();
        return feeStructureRepository.findBySchoolId(schoolId, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public FeeStructureView getById(UserPrincipal principal, Long id) {
        FeesAccessGuard.assertAnyFeeViewer(principal);
        Long schoolId = principal.getSchoolId();
        FeeStructure structure = feeStructureRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Fee structure not found"));
        return toView(structure);
    }

    @Transactional
    public void activate(UserPrincipal principal, Long id) {
        FeesAccessGuard.assertAdminOrAccountant(principal);
        Long schoolId = principal.getSchoolId();
        FeeStructure structure = feeStructureRepository.findBySchoolIdAndId(schoolId, id)
                .orElseThrow(() -> new FeesRuleException("RESOURCE_NOT_FOUND", NOT_FOUND,
                        "Fee structure not found"));
        structure.setStatus(FeeStructureStatus.ACTIVE);
        feeStructureRepository.save(structure);
    }

    private FeeStructureView toView(FeeStructure structure) {
        List<FeeItem> items = structure.getItems();
        List<FeeStructureView.FeeItemView> itemViews = items.stream()
                .map(item -> new FeeStructureView.FeeItemView(
                        item.getId(), item.getName(), item.getAmount(), item.isMandatory()))
                .toList();
        BigDecimal total = items.stream()
                .map(FeeItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FeeStructureView(
                structure.getId(), structure.getSchoolId(), structure.getTermId(),
                structure.getClassLevelId(), structure.getName(), structure.getStatus(),
                itemViews, total, structure.getCreatedAt());
    }
}
