package com.peterscode.edutrack.library.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loans")
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class Loan extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "copy_id", nullable = false)
    private Long copyId;

    @Column(name = "borrower_type", nullable = false, length = 20)
    private String borrowerType;

    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;

    @Column(name = "issued_by", nullable = false)
    private Long issuedBy;

    @Column(name = "issued_on", nullable = false)
    private Instant issuedOn;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "renewal_count", nullable = false)
    private Integer renewalCount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "active_marker", columnDefinition = "UUID")
    private UUID activeMarker;

    @Version
    @Column(nullable = false)
    private Long version;
}
