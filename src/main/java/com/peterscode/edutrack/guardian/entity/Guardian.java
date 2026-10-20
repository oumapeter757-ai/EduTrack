package com.peterscode.edutrack.guardian.entity;

import com.peterscode.edutrack.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
    name = "guardians",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_guardian_school_email",
            columnNames = {"school_id", "email"}),
    indexes = {
      @Index(name = "idx_guardian_school", columnList = "school_id"),
      @Index(name = "idx_guardian_user", columnList = "user_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Guardian extends BaseEntity {
  @Column(name = "school_id", nullable = false)
  Long schoolId;

  @Column(name = "user_id")
  Long userId;

  @Column(name = "first_name", nullable = false)
  String firstName;

  @Column(name = "last_name", nullable = false)
  String lastName;

  @Column(length = 255)
  String email;

  @Column(nullable = false, length = 50)
  String phone;

  @Column(name = "alternate_phone", length = 50)
  String alternatePhone;

  @Column(nullable = false, length = 50)
  String relationship;

  @Column(length = 100)
  String occupation;

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

  @Column(nullable = false)
  @Builder.Default
  boolean active = true;

  public String getFullName() {
    return (firstName + " " + lastName).trim();
  }
}
