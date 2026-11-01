package com.peterscode.edutrack.user.repository;

import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySchoolIdAndId(Long schoolId, Long id);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    List<User> findByRole(Role role);

    long countBySchoolIdAndRoleAndActiveTrue(Long schoolId, Role role);
}
