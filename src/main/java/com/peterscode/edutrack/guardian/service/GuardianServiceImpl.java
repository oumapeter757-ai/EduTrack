package com.peterscode.edutrack.guardian.service;

import com.peterscode.edutrack.guardian.dto.*;
import com.peterscode.edutrack.guardian.entity.Guardian;
import com.peterscode.edutrack.guardian.mapper.GuardianMapper;
import com.peterscode.edutrack.guardian.repository.GuardianRepository;
import com.peterscode.edutrack.school.repository.SchoolRepository;
import com.peterscode.edutrack.security.UserPrincipal;
import com.peterscode.edutrack.shared.exception.*;
import com.peterscode.edutrack.shared.response.PageResponse;
import com.peterscode.edutrack.user.entity.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peterscode.edutrack.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class GuardianServiceImpl implements GuardianService {
  private final GuardianRepository repo;
  private final SchoolRepository schools;
  private final GuardianMapper mapper;
  private final UserRepository users;

  private Long school(Long r, UserPrincipal p) {
    if (p == null || p.getRole() == null)
      throw new UnauthorizedOperationException("User is not authenticated");
    if (p.getRole() == Role.SUPER_ADMIN) {
      if (r == null) throw new SchoolRequiredException();
      return r;
    }

    if (p.getSchoolId() == null || (r != null && !r.equals(p.getSchoolId())))
      throw new SchoolNotFoundException("School not found");
    return p.getSchoolId();
  }

  private void requireAdmin(UserPrincipal p) {
    if (p == null || (p.getRole() != Role.SUPER_ADMIN && p.getRole() != Role.SCHOOL_ADMIN)) {
      throw new UnauthorizedOperationException("Only school administrators can access guardian records");
    }
  }

  private Guardian find(Long id, UserPrincipal p) {
    if (p == null || p.getRole() == null)
      throw new UnauthorizedOperationException("User is not authenticated");
    Guardian g =
        repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Guardian", "id", id));
    if (p.getRole() != Role.SUPER_ADMIN && !g.getSchoolId().equals(p.getSchoolId()))
      throw new ResourceNotFoundException("Guardian", "id", id);
    return g;
  }

  @Transactional
  public GuardianResponseDto create(CreateGuardianRequestDto d, UserPrincipal p) {
    Long sid = school(d.getSchoolId(), p);
    var school = schools.findById(sid).orElseThrow(() -> new SchoolNotFoundException(sid));
    if (!school.isActive()) throw new SchoolInactiveException("School is inactive");
    if (d.getEmail() != null
        && repo.existsBySchoolIdAndEmail(sid, d.getEmail().trim().toLowerCase()))
      throw new DuplicateResourceException("Guardian", "email", d.getEmail());

    if (d.getUserId() != null) {
      var user = users.findById(d.getUserId())
          .orElseThrow(() -> new ResourceNotFoundException("User", "id", d.getUserId()));
      if (!sid.equals(user.getSchoolId())) {
        throw new ResourceNotFoundException("User", "id", d.getUserId());
      }
    }

    return mapper.toDto(repo.save(mapper.toEntity(d, sid)));
  }

  @Transactional(readOnly = true)
  public GuardianResponseDto get(Long id, UserPrincipal p) {
    requireAdmin(p);
    return mapper.toDto(find(id, p));
  }

  @Transactional(readOnly = true)
  public PageResponse<GuardianResponseDto> list(Long sid, Pageable page, UserPrincipal p) {
    requireAdmin(p);
    java.util.Set<String> allowedSorts = java.util.Set.of("id", "firstName", "lastName", "relationship", "email", "phone");
    Pageable safePageable = com.peterscode.edutrack.shared.PageableSortValidator.validate(page, allowedSorts);
    Page<GuardianResponseDto> x =
        (p.getRole() == Role.SUPER_ADMIN && sid == null
                ? repo.findAll(safePageable)
                : repo.findBySchoolId(school(sid, p), safePageable))
            .map(mapper::toDto);
    return PageResponse.from(x);
  }

  @Transactional
  public GuardianResponseDto update(Long id, UpdateGuardianRequestDto d, UserPrincipal p) {
    Guardian g = find(id, p);
    if (d.getEmail() != null
        && repo.existsBySchoolIdAndEmailAndIdNot(
            g.getSchoolId(), d.getEmail().trim().toLowerCase(), id))
      throw new DuplicateResourceException("Guardian", "email", d.getEmail());
    mapper.update(d, g);
    return mapper.toDto(repo.save(g));
  }

  @Transactional
  public void delete(Long id, UserPrincipal p) {
    Guardian g = find(id, p);
    g.setActive(false);
    repo.save(g);
  }
}
