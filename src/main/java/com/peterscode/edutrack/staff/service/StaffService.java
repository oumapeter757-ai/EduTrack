package com.peterscode.edutrack.staff.service;

import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.staff.dto.*;
import org.springframework.data.domain.Pageable;

public interface StaffService {
  StaffResponseDto create(CreateStaffRequestDto d, UserPrincipal p);

  StaffResponseDto get(Long id, UserPrincipal p);

  PageResponse<StaffResponseDto> list(Long schoolId, Pageable page, UserPrincipal p);

  StaffResponseDto update(Long id, UpdateStaffRequestDto d, UserPrincipal p);

  void delete(Long id, UserPrincipal p);
}
