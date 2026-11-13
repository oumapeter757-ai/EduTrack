package com.peterscode.edutrack.user.repository;

import com.peterscode.edutrack.user.entity.Role;
import com.peterscode.edutrack.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT user FROM User user WHERE user.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    Optional<User> findBySchoolIdAndId(Long schoolId, Long id);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    /**
     * Find users by role — use for small result sets (e.g., SUPER_ADMIN lookup).
     * For large result sets, use {@link #findByRole(Role, Pageable)} instead.
     */
    List<User> findByRole(Role role);

    /**
     * Paginated version of {@link #findByRole(Role)} for large result sets.
     */
    Page<User> findByRole(Role role, Pageable pageable);

    long countBySchoolIdAndRoleAndActiveTrue(Long schoolId, Role role);
}
