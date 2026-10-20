package com.peterscode.edutrack.staff.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import com.peterscode.edutrack.staff.enums.StaffStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "staff",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_staff_school_staff_number",
            columnNames = {"school_id", "staff_number"}),
    indexes = {
      @Index(name = "idx_staff_school", columnList = "school_id"),
      @Index(name = "idx_staff_school_department", columnList = "school_id,department"),
      @Index(name = "idx_staff_school_status", columnList = "school_id,status")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Staff extends BaseEntity {
  @Column(name = "school_id", nullable = false)
  Long schoolId;

  @Column(name = "user_id")
  Long userId;

  @Column(name = "staff_number", nullable = false, length = 50)
  String staffNumber;

  @Column(name = "first_name", nullable = false, length = 100)
  String firstName;

  @Column(name = "last_name", nullable = false, length = 100)
  String lastName;

  @Column(name = "middle_name")
  String middleName;

  @Column(length = 255)
  String email;

  @Column(length = 50)
  String phone;

  @Column(length = 20)
  String gender;

  @Column(name = "date_of_birth")
  LocalDate dateOfBirth;

  @Column(name = "join_date")
  LocalDate joinDate;

  @Column(length = 100)
  String department;

  @Column(length = 100)
  String designation;

  @Column(length = 255)
  String qualification;

  @Column(name = "address_line_1")
  String addressLine1;

  @Column(name = "address_line_2")
  String addressLine2;

  @Column(length = 100)
  String city;

  @Column(length = 100)
  String state;

  @Column(length = 100)
  String country;

  @Column(name = "postal_code", length = 20)
  String postalCode;

  @Column(name = "profile_image_url", length = 500)
  String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  @Builder.Default
  StaffStatus status = StaffStatus.ACTIVE;

  @Column(nullable = false)
  @Builder.Default
  boolean active = true;

  public String getFullName() {
    return (firstName + " " + (middleName == null ? "" : middleName + " ") + lastName).trim();
  }
}
