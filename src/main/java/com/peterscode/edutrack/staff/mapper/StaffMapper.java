package com.peterscode.edutrack.staff.mapper;

import com.peterscode.edutrack.staff.dto.*;
import com.peterscode.edutrack.staff.entity.Staff;
import org.springframework.stereotype.Component;

@Component
public class StaffMapper {
  public Staff toEntity(CreateStaffRequestDto d, Long s) {
    return Staff.builder()
        .schoolId(s)
        .userId(d.getUserId())
        .staffNumber(d.getStaffNumber().trim().toUpperCase())
        .firstName(d.getFirstName().trim())
        .lastName(d.getLastName().trim())
        .middleName(d.getMiddleName())
        .email(norm(d.getEmail()))
        .phone(d.getPhone())
        .gender(d.getGender())
        .dateOfBirth(d.getDateOfBirth())
        .joinDate(d.getJoinDate())
        .department(d.getDepartment())
        .designation(d.getDesignation())
        .qualification(d.getQualification())
        .addressLine1(d.getAddressLine1())
        .addressLine2(d.getAddressLine2())
        .city(d.getCity())
        .state(d.getState())
        .country(d.getCountry())
        .postalCode(d.getPostalCode())
        .profileImageUrl(d.getProfileImageUrl())
        .status(
            d.getStatus() == null
                ? com.peterscode.edutrack.staff.enums.StaffStatus.ACTIVE
                : d.getStatus())
        .build();
  }

  private String norm(String x) {
    return x == null ? null : x.trim().toLowerCase();
  }

  public StaffResponseDto toDto(Staff s) {
    return StaffResponseDto.builder()
        .id(s.getId())
        .schoolId(s.getSchoolId())
        .userId(s.getUserId())
        .staffNumber(s.getStaffNumber())
        .firstName(s.getFirstName())
        .lastName(s.getLastName())
        .middleName(s.getMiddleName())
        .fullName(s.getFullName())
        .email(s.getEmail())
        .phone(s.getPhone())
        .gender(s.getGender())
        .dateOfBirth(s.getDateOfBirth())
        .joinDate(s.getJoinDate())
        .department(s.getDepartment())
        .designation(s.getDesignation())
        .qualification(s.getQualification())
        .addressLine1(s.getAddressLine1())
        .addressLine2(s.getAddressLine2())
        .city(s.getCity())
        .state(s.getState())
        .country(s.getCountry())
        .postalCode(s.getPostalCode())
        .profileImageUrl(s.getProfileImageUrl())
        .status(s.getStatus())
        .active(s.isActive())
        .createdAt(s.getCreatedAt())
        .updatedAt(s.getUpdatedAt())
        .build();
  }

  public void update(UpdateStaffRequestDto d, Staff s) {
    if (d.getFirstName() != null) s.setFirstName(d.getFirstName());
    if (d.getLastName() != null) s.setLastName(d.getLastName());
    if (d.getMiddleName() != null) s.setMiddleName(d.getMiddleName());
    if (d.getEmail() != null) s.setEmail(norm(d.getEmail()));
    if (d.getPhone() != null) s.setPhone(d.getPhone());
    if (d.getGender() != null) s.setGender(d.getGender());
    if (d.getDateOfBirth() != null) s.setDateOfBirth(d.getDateOfBirth());
    if (d.getJoinDate() != null) s.setJoinDate(d.getJoinDate());
    if (d.getDepartment() != null) s.setDepartment(d.getDepartment());
    if (d.getDesignation() != null) s.setDesignation(d.getDesignation());
    if (d.getQualification() != null) s.setQualification(d.getQualification());
    if (d.getAddressLine1() != null) s.setAddressLine1(d.getAddressLine1());
    if (d.getAddressLine2() != null) s.setAddressLine2(d.getAddressLine2());
    if (d.getCity() != null) s.setCity(d.getCity());
    if (d.getState() != null) s.setState(d.getState());
    if (d.getCountry() != null) s.setCountry(d.getCountry());
    if (d.getPostalCode() != null) s.setPostalCode(d.getPostalCode());
    if (d.getProfileImageUrl() != null) s.setProfileImageUrl(d.getProfileImageUrl());
    if (d.getStatus() != null) s.setStatus(d.getStatus());
    if (d.getActive() != null) s.setActive(d.getActive());
  }
}

