package com.peterscode.edutrack.library.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "book_copies")
@Setter
@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class BookCopy extends BaseEntity {

    @Column(name = "school_id", nullable = false)
    private Long schoolId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "book_number", nullable = false, length = 50)
    private String bookNumber;

    @Column(name = "shelf_location", length = 100)
    private String shelfLocation;

    @Column(length = 50)
    private String condition;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "acquired_on")
    private LocalDate acquiredOn;

    @Column(name = "lost_charge_hint", precision = 10, scale = 2)
    private BigDecimal lostChargeHint;

    @Version
    @Column(nullable = false)
    private Long version;
}
