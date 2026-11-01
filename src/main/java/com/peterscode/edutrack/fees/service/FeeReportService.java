package com.peterscode.edutrack.fees.service;

import com.peterscode.edutrack.classlevel.entity.ClassLevel;
import com.peterscode.edutrack.classlevel.repository.ClassLevelRepository;
import com.peterscode.edutrack.fees.dto.ArrearsByClassLevelView;
import com.peterscode.edutrack.fees.dto.CollectionByChannelView;
import com.peterscode.edutrack.fees.dto.CollectionByDayView;
import com.peterscode.edutrack.fees.dto.ReconciliationDayView;
import com.peterscode.edutrack.fees.exception.FeesRuleException;
import com.peterscode.edutrack.fees.repository.FeePaymentRepository;
import com.peterscode.edutrack.fees.repository.LedgerEntryRepository;
import com.peterscode.edutrack.fees.repository.StudentFeeAccountRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;

/**
 * Report service for fees data: collection summaries, arrears by class level,
 * and daily reconciliation between provider payments and ledger entries.
 * <p>
 * All reports are school-scoped and read-only.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeeReportService {

    private final FeePaymentRepository feePaymentRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final StudentFeeAccountRepository studentFeeAccountRepository;
    private final ClassLevelRepository classLevelRepository;

    /**
     * Collection report grouped by day for a date range.
     */
    public List<CollectionByDayView> collectionByDay(UserPrincipal principal, LocalDate from, LocalDate to) {
        Long schoolId = assertSchoolAdminOrAccountant(principal);
        List<Object[]> rows = feePaymentRepository.sumSucceededByDay(schoolId, from, to);
        List<CollectionByDayView> result = new ArrayList<>();
        for (Object[] row : rows) {
            LocalDate day = toLocalDate(row[0]);
            BigDecimal total = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();
            String currency = (String) row[3];
            result.add(new CollectionByDayView(day, total, (int) count, currency));
        }
        return result;
    }

    /**
     * Collection report grouped by channel for a date range.
     */
    public List<CollectionByChannelView> collectionByChannel(UserPrincipal principal, LocalDate from, LocalDate to) {
        Long schoolId = assertSchoolAdminOrAccountant(principal);
        List<Object[]> rows = feePaymentRepository.sumSucceededByChannel(schoolId, from, to);
        List<CollectionByChannelView> result = new ArrayList<>();
        for (Object[] row : rows) {
            String channel = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();
            String currency = (String) row[3];
            result.add(new CollectionByChannelView(channel, total, (int) count, currency));
        }
        return result;
    }

    /**
     * Arrears report grouped by class level.
     * Shows only ACTIVE students with positive cached_balance (money owed).
     */
    public List<ArrearsByClassLevelView> arrearsByClassLevel(UserPrincipal principal) {
        Long schoolId = assertSchoolAdminOrAccountant(principal);
        List<Object[]> rows = studentFeeAccountRepository.arrearsByClassLevel(schoolId);

        // Fetch class level names
        List<Long> classLevelIds = new ArrayList<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                classLevelIds.add(((Number) row[0]).longValue());
            }
        }
        Map<Long, String> classLevelNames = classLevelRepository
                .findAllById(classLevelIds)
                .stream()
                .collect(Collectors.toMap(ClassLevel::getId, ClassLevel::getName));

        List<ArrearsByClassLevelView> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long classLevelId = row[0] != null ? ((Number) row[0]).longValue() : null;
            long count = ((Number) row[1]).longValue();
            BigDecimal total = (BigDecimal) row[2];
            String currency = (String) row[3];
            String name = classLevelId != null ? classLevelNames.getOrDefault(classLevelId, "Unknown") : "No Class";
            result.add(new ArrearsByClassLevelView(classLevelId, name, (int) count, total, currency));
        }
        return result;
    }

    /**
     * Daily reconciliation: compares provider-confirmed payments (fee_payments table)
     * with ledger entries (ledger_entries table) for each day.
     * Differences indicate payments confirmed by provider but not yet posted, or vice versa.
     */
    public List<ReconciliationDayView> dailyReconciliation(UserPrincipal principal, LocalDate from, LocalDate to) {
        Long schoolId = assertSchoolAdminOrAccountant(principal);

        // Get provider-confirmed payments by day
        List<Object[]> paymentRows = feePaymentRepository.sumSucceededByDayForReconciliation(schoolId, from, to);
        Map<LocalDate, PaymentSummary> paymentMap = new java.util.HashMap<>();
        for (Object[] row : paymentRows) {
            LocalDate day = toLocalDate(row[0]);
            BigDecimal total = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();
            String currency = (String) row[3];
            paymentMap.put(day, new PaymentSummary(total, (int) count, currency));
        }

        // Get ledger payment entries by day
        List<Object[]> ledgerRows = ledgerEntryRepository.sumPaymentsByDay(schoolId, from, to);
        Map<LocalDate, LedgerSummary> ledgerMap = new java.util.HashMap<>();
        for (Object[] row : ledgerRows) {
            LocalDate day = toLocalDate(row[0]);
            BigDecimal total = (BigDecimal) row[1]; // negative for payments
            long count = ((Number) row[2]).longValue();
            ledgerMap.put(day, new LedgerSummary(total.abs(), (int) count));
        }

        // Merge all dates
        java.util.Set<LocalDate> allDates = new java.util.LinkedHashSet<>();
        allDates.addAll(paymentMap.keySet());
        allDates.addAll(ledgerMap.keySet());

        List<ReconciliationDayView> result = new ArrayList<>();
        for (LocalDate day : allDates) {
            PaymentSummary ps = paymentMap.getOrDefault(day, new PaymentSummary(BigDecimal.ZERO, 0, "KES"));
            LedgerSummary ls = ledgerMap.getOrDefault(day, new LedgerSummary(BigDecimal.ZERO, 0));
            BigDecimal difference = ps.total().subtract(ls.total());
            result.add(new ReconciliationDayView(
                    day,
                    ps.total(),
                    ls.total(),
                    difference,
                    ps.count(),
                    ls.count(),
                    ps.currency()
            ));
        }
        return result;
    }

    private Long assertSchoolAdminOrAccountant(UserPrincipal principal) {
        if (principal.getSchoolId() == null) {
            throw new FeesRuleException("INSUFFICIENT_PERMISSION", FORBIDDEN,
                    "School context required for reports");
        }
        return principal.getSchoolId();
    }

    private LocalDate toLocalDate(Object obj) {
        if (obj instanceof LocalDate ld) return ld;
        if (obj instanceof java.sql.Date sd) return sd.toLocalDate();
        if (obj instanceof String s) return LocalDate.parse(s);
        return LocalDate.parse(obj.toString());
    }

    record PaymentSummary(BigDecimal total, int count, String currency) {}
    record LedgerSummary(BigDecimal total, int count) {}
}
