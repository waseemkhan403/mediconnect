package com.mediconnect.repository;

import com.mediconnect.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Staff sign-in: the email lives on the staff row. */
    @Query("""
            SELECT u FROM User u
            JOIN FETCH u.staff s
            WHERE LOWER(s.email) = LOWER(:email)
            """)
    Optional<User> findByStaffEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByStaffId(Long staffId);

    /** The login id of a staff member (chat delivery to a doctor). */
    @Query("SELECT u.id FROM User u WHERE u.staff.id = :staffId")
    Optional<Long> findIdByStaffId(Long staffId);

    /** Is there any admin login at all? (startup bootstrap) */
    boolean existsByStaff_StaffType(String staffType);

    /** User with the staff row loaded (the role and name come from it). */
    @Query("""
            SELECT u FROM User u
            LEFT JOIN FETCH u.staff
            WHERE u.id = :id
            """)
    Optional<User> findDetailById(Long id);

    /** role: a staff type, or PATIENT for patient logins. Pass null to skip a filter. */
    @Query(value = """
            SELECT u FROM User u
            LEFT JOIN FETCH u.staff s
            WHERE (CAST(:role AS String) IS NULL
                   OR (CAST(:role AS String) = 'PATIENT' AND s IS NULL)
                   OR s.staffType = :role)
              AND (CAST(:active AS Boolean) IS NULL OR u.active = :active)
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            LEFT JOIN u.staff s
            WHERE (CAST(:role AS String) IS NULL
                   OR (CAST(:role AS String) = 'PATIENT' AND s IS NULL)
                   OR s.staffType = :role)
              AND (CAST(:active AS Boolean) IS NULL OR u.active = :active)
            """)
    Page<User> findAllWithFilters(String role, Boolean active, Pageable pageable);
}
