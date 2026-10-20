package com.peterscode.edutrack.staff.service;

import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.*;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.staff.dto.*;
import com.peterscode.edutrack.staff.entity.Staff;
import com.peterscode.edutrack.staff.mapper.StaffMapper;
import com.peterscode.edutrack.staff.repository.StaffRepository;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peterscode.edutrack.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements StaffService {
  private final StaffRepository repo;
  private final SchoolRepository schools;
  private final StaffMapper mapper;
  private final UserRepository users;

  private Long school(Long requested, UserPrincipal p) {
    if (p == null || p.getRole() == null)
      throw new UnauthorizedOperationException("User is not authenticated");
    if (p.getRole() == Role.SUPER_ADMIN) {
      if (requested == null) throw new SchoolRequiredException();
      return requested;
    }

    if (p.getSchoolId() == null || (requested != null && !requested.equals(p.getSchoolId())))
      throw new SchoolNotFoundException("School not found");
    return p.getSchoolId();
  }

  private void requireAdmin(UserPrincipal p) {
    if (p == null || (p.getRole() != Role.SUPER_ADMIN && p.getRole() != Role.SCHOOL_ADMIN)) {
      throw new UnauthorizedOperationException("Only school administrators can access staff records");
    }
  }

  private Staff find(Long id, UserPrincipal p) {
    if (p == null || p.getRole() == null) {
      throw new UnauthorizedOperationException("User is not authenticated");
    }
    Staff s = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Staff", "id", id));
    if (p.getRole() != Role.SUPER_ADMIN && !s.getSchoolId().equals(p.getSchoolId()))
      throw new ResourceNotFoundException("Staff", "id", id);
    return s;
  }

  @Transactional
  public StaffResponseDto create(CreateStaffRequestDto d, UserPrincipal p) {
    Long sid = school(d.getSchoolId(), p);
    var school = schools.findById(sid).orElseThrow(() -> new SchoolNotFoundException(sid));
    if (!school.isActive()) throw new SchoolInactiveException("School is inactive");

    String n = d.getStaffNumber().trim().toUpperCase();
    if (repo.existsBySchoolIdAndStaffNumber(sid, n))
      throw new DuplicateResourceException("Staff", "staffNumber", n);

    if (d.getEmail() != null && !d.getEmail().isBlank()) {
      String email = d.getEmail().trim().toLowerCase();
      if (repo.existsBySchoolIdAndEmail(sid, email)) {
        throw new DuplicateResourceException("Staff", "email", email);
      }
    }

    if (d.getUserId() != null) {
      var user = users.findById(d.getUserId())
          .orElseThrow(() -> new ResourceNotFoundException("User", "id", d.getUserId()));
      if (!sid.equals(user.getSchoolId())) {
        throw new ResourceNotFoundException("User", "id", d.getUserId());
      }
    }

    Staff s = mapper.toEntity(d, sid);
    return mapper.toDto(repo.save(s));
  }

  @Transactional(readOnly = true)
  public StaffResponseDto get(Long id, UserPrincipal p) {
    if (p == null || p.getRole() == null) {
      throw new UnauthorizedOperationException("User is not authenticated");
    }
    if (p.getRole() != Role.SUPER_ADMIN && p.getRole() != Role.SCHOOL_ADMIN && p.getRole() != Role.TEACHER) {
      throw new UnauthorizedOperationException("Access denied");
    }
    Staff staff = find(id, p);
    if (p.getRole() == Role.TEACHER) {
      if (staff.getUserId() == null || !staff.getUserId().equals(p.getId())) {
        throw new ResourceNotFoundException("Staff", "id", id);
      }
    }
    return mapper.toDto(staff);
  }

  @Transactional(readOnly = true)
  public PageResponse<StaffResponseDto> list(Long sid, Pageable page, UserPrincipal p) {
    requireAdmin(p);
    java.util.Set<String> allowedSorts = java.util.Set.of("id", "firstName", "lastName", "staffNumber", "department", "status");
    Pageable safePageable = com.peterscode.edutrack.shared.PageableSortValidator.validate(page, allowedSorts);
    Page<StaffResponseDto> x =
        (p.getRole() == Role.SUPER_ADMIN && sid == null
                ? repo.findAll(safePageable)
                : repo.findBySchoolId(school(sid, p), safePageable))
            .map(mapper::toDto);
    return PageResponse.from(x);
  }

  @Transactional
  public StaffResponseDto update(Long id, UpdateStaffRequestDto d, UserPrincipal p) {
    Staff s = find(id, p);
    if (d.getEmail() != null
        && repo.existsBySchoolIdAndEmailAndIdNot(
            s.getSchoolId(), d.getEmail().trim().toLowerCase(), id))
      throw new DuplicateResourceException("Staff", "email", d.getEmail());
    mapper.update(d, s);
    return mapper.toDto(repo.save(s));
  }

  @Transactional
  public void delete(Long id, UserPrincipal p) {
    Staff s = find(id, p);
    s.setActive(false);
    s.setStatus(com.peterscode.edutrack.staff.enums.StaffStatus.TERMINATED);
    repo.save(s);
  }
}
