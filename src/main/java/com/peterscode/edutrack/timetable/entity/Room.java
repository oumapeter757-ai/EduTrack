package com.peterscode.edutrack.timetable.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(name = "uk_room_school_name", columnNames = {"school_id", "name"}),
        indexes = @Index(name = "idx_room_school", columnList = "school_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Room extends BaseEntity {
    @Column(name = "school_id", nullable = false)
    private Long schoolId;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 32)
    private RoomType roomType;
    @Column(nullable = false)
    private Integer capacity;
    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    public enum RoomType { CLASSROOM, LAB, HALL, FIELD }
}
