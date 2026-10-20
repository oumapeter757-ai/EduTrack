package com.peterscode.edutrack.guardian.mapper;

import com.peterscode.edutrack.guardian.dto.*;
import com.peterscode.edutrack.guardian.entity.Guardian;
import org.springframework.stereotype.Component;

@Component
public class GuardianMapper {
  public Guardian toEntity(CreateGuardianRequestDto d, Long s) {
    return Guardian.builder()
        .schoolId(s)
        .userId(d.getUserId())
        .firstName(d.getFirstName().trim())
        .lastName(d.getLastName().trim())
        .email(n(d.getEmail()))
        .phone(d.getPhone())
        .alternatePhone(d.getAlternatePhone())
        .relationship(d.getRelationship())
        .occupation(d.getOccupation())
        .addressLine1(d.getAddressLine1())
        .addressLine2(d.getAddressLine2())
        .city(d.getCity())
        .state(d.getState())
        .country(d.getCountry())
        .postalCode(d.getPostalCode())
        .build();
  }

  private String n(String x) {
    return x == null ? null : x.trim().toLowerCase();
  }

  public GuardianResponseDto toDto(Guardian g) {
    return GuardianResponseDto.builder()
        .id(g.getId())
        .schoolId(g.getSchoolId())
        .userId(g.getUserId())
        .firstName(g.getFirstName())
        .lastName(g.getLastName())
        .fullName(g.getFullName())
        .email(g.getEmail())
        .phone(g.getPhone())
        .alternatePhone(g.getAlternatePhone())
        .relationship(g.getRelationship())
        .occupation(g.getOccupation())
        .addressLine1(g.getAddressLine1())
        .addressLine2(g.getAddressLine2())
        .city(g.getCity())
        .state(g.getState())
        .country(g.getCountry())
        .postalCode(g.getPostalCode())
        .active(g.isActive())
        .createdAt(g.getCreatedAt())
        .updatedAt(g.getUpdatedAt())
        .build();
  }

  public void update(UpdateGuardianRequestDto d, Guardian g) {
    if (d.getFirstName() != null) g.setFirstName(d.getFirstName());
    if (d.getLastName() != null) g.setLastName(d.getLastName());
    if (d.getEmail() != null) g.setEmail(n(d.getEmail()));
    if (d.getPhone() != null) g.setPhone(d.getPhone());
    if (d.getAlternatePhone() != null) g.setAlternatePhone(d.getAlternatePhone());
    if (d.getRelationship() != null) g.setRelationship(d.getRelationship());
    if (d.getOccupation() != null) g.setOccupation(d.getOccupation());
    if (d.getAddressLine1() != null) g.setAddressLine1(d.getAddressLine1());
    if (d.getAddressLine2() != null) g.setAddressLine2(d.getAddressLine2());
    if (d.getCity() != null) g.setCity(d.getCity());
    if (d.getState() != null) g.setState(d.getState());
    if (d.getCountry() != null) g.setCountry(d.getCountry());
    if (d.getPostalCode() != null) g.setPostalCode(d.getPostalCode());
    if (d.getActive() != null) g.setActive(d.getActive());
  }
}
