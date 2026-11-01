package com.peterscode.edutrack.library.service;

import com.peterscode.edutrack.library.audit.LibraryAuditService;
import com.peterscode.edutrack.library.dto.LibraryViews;
import com.peterscode.edutrack.library.entity.LibraryFine;
import com.peterscode.edutrack.library.exception.LibraryRuleException;
import com.peterscode.edutrack.library.repository.LibraryFineRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LibraryFineService {

    private final LibraryFineRepository fineRepository;
    private final LibraryAuditService auditService;

    @Transactional
    public LibraryViews.FineView assessFine(Long schoolId, Long fineId, BigDecimal amount,
                                             String reason, UserPrincipal principal) {
        if (principal.getRole() == Role.LIBRARIAN) {
            throw new LibraryRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN,
                    "Librarians cannot assess fines");
        }

        LibraryFine fine = fineRepository.findBySchoolIdAndId(schoolId, fineId)
                .orElseThrow(() -> new LibraryRuleException("FINE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Fine not found"));

        if (!"PENDING_ASSESSMENT".equals(fine.getStatus())) {
            throw new LibraryRuleException("FINE_ALREADY_ASSESSED", HttpStatus.CONFLICT,
                    "Fine has already been assessed");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new LibraryRuleException("INVALID_AMOUNT", HttpStatus.BAD_REQUEST,
                    "Amount must be non-negative");
        }

        fine.setAmount(amount.setScale(2));
        fine.setStatus("UNPAID");
        fine.setAssessedBy(principal.getId());
        fine.setAssessedAt(Instant.now());
        fine = fineRepository.save(fine);

        auditService.append(schoolId, "FINE", fine.getId(), "ASSESS", principal.getId(),
                "status=PENDING_ASSESSMENT", "status=UNPAID,amount=" + amount, reason);

        return toFineView(fine);
    }

    @Transactional
    public LibraryViews.FineView waiveFine(Long schoolId, Long fineId, String reason,
                                            UserPrincipal principal) {
        if (principal.getRole() == Role.LIBRARIAN) {
            throw new LibraryRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN,
                    "Librarians cannot waive fines");
        }

        LibraryFine fine = fineRepository.findBySchoolIdAndId(schoolId, fineId)
                .orElseThrow(() -> new LibraryRuleException("FINE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Fine not found"));

        if (!"UNPAID".equals(fine.getStatus())) {
            throw new LibraryRuleException("FINE_NOT_WAIVABLE", HttpStatus.CONFLICT,
                    "Fine cannot be waived in current status");
        }

        fine.setStatus("WAIVED");
        fine.setWaivedReason(reason);
        fine = fineRepository.save(fine);

        auditService.append(schoolId, "FINE", fine.getId(), "WAIVE", principal.getId(),
                "status=UNPAID", "status=WAIVED", reason);

        return toFineView(fine);
    }

    @Transactional
    public LibraryViews.FineView payFine(Long schoolId, Long fineId, String reference,
                                          UserPrincipal principal) {
        if (principal.getRole() == Role.LIBRARIAN) {
            throw new LibraryRuleException("ACCESS_DENIED", HttpStatus.FORBIDDEN,
                    "Librarians cannot record fine payments");
        }

        LibraryFine fine = fineRepository.findBySchoolIdAndId(schoolId, fineId)
                .orElseThrow(() -> new LibraryRuleException("FINE_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "Fine not found"));

        if (!"UNPAID".equals(fine.getStatus())) {
            throw new LibraryRuleException("FINE_NOT_PAYABLE", HttpStatus.CONFLICT,
                    "Fine cannot be paid in current status");
        }

        fine.setStatus("PAID");
        fine.setPaidReference(reference);
        fine = fineRepository.save(fine);

        auditService.append(schoolId, "FINE", fine.getId(), "PAY", principal.getId(),
                "status=UNPAID", "status=PAID,reference=" + reference, null);

        return toFineView(fine);
    }

    private LibraryViews.FineView toFineView(LibraryFine fine) {
        return new LibraryViews.FineView(
                fine.getId(),
                fine.getReason(),
                fine.getOverdueDays(),
                fine.getSuggestedAmount(),
                fine.getAmount(),
                fine.getStatus()
        );
    }
}
