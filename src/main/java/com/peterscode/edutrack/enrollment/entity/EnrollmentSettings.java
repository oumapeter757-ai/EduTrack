package com.peterscode.edutrack.enrollment.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "enrollment_settings", uniqueConstraints = @UniqueConstraint(
        name = "uk_enrollment_settings_school", columnNames = "school_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class EnrollmentSettings extends BaseEntity {
    @Column(name = "school_id", nullable = false) private Long schoolId;
    @Column(name = "suggested_min_average", precision = 8, scale = 3) private BigDecimal suggestedMinAverage;
    @Column(name = "block_on_unpaid_fees", nullable = false) private boolean blockOnUnpaidFees = false;
    @Column(name = "block_on_open_library_loans", nullable = false) private boolean blockOnOpenLibraryLoans = false;
    @Column(name = "require_closed_term", nullable = false) private boolean requireClosedTerm = true;
    @Column(name = "undo_window_days", nullable = false) private int undoWindowDays = 7;
    @Column(name = "alumni_login_days", nullable = false) private int alumniLoginDays = 365;
    @Column(name = "batch_size", nullable = false) private int batchSize = 100;
}
