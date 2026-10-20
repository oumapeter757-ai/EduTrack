package com.peterscode.edutrack.guardian.service;

import com.peterscode.edutrack.guardian.dto.*;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface GuardianService {
  GuardianResponseDto create(CreateGuardianRequestDto d, UserPrincipal p);

  GuardianResponseDto get(Long id, UserPrincipal p);

  PageResponse<GuardianResponseDto> list(Long schoolId, Pageable page, UserPrincipal p);

  GuardianResponseDto update(Long id, UpdateGuardianRequestDto d, UserPrincipal p);

  void delete(Long id, UserPrincipal p);
}
