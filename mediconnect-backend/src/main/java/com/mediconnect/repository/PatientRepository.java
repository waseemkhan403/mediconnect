package com.mediconnect.repository;

import com.mediconnect.entity.Patient;
import com.mediconnect.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUhid(String uhid);

    /** Next number from the sequence created in V3; the service formats it into a UHID. */
    @Query(value = "SELECT nextval('patient_uhid_seq')", nativeQuery = true)
    Long nextUhidSequence();

    /**
     * Duplicate guard before registering. Phone alone is not unique (family members share it),
     * so the combination name + date of birth + phone is what identifies "the same person".
     */
    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndPhone(String fullName, LocalDate dateOfBirth, String phone);

    /** Same check for updates: ignore the patient being edited. (NEW in this step) */
    boolean existsByFullNameIgnoreCaseAndDateOfBirthAndPhoneAndIdNot(
            String fullName, LocalDate dateOfBirth, String phone, Long id);

    /** ownerId: set for patient logins so they only see their own records; null for staff. */
    @Query("""
            SELECT p FROM Patient p
            LEFT JOIN p.owner o
            WHERE (CAST(:name AS String) IS NULL
                   OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
              AND (CAST(:phone AS String) IS NULL OR p.phone = :phone)
              AND (CAST(:uhid AS String) IS NULL OR p.uhid = :uhid)
              AND (CAST(:active AS Boolean) IS NULL OR p.active = :active)
              AND (CAST(:ownerId AS Long) IS NULL OR o.id = :ownerId)
            """)
    Page<Patient> findAllWithFilters(String name, String phone, String uhid, Boolean active, Long ownerId,
                                     Pageable pageable);

    /**
     * On patient login: records registered at reception with this phone, and not yet claimed, become owned by
     * this login. Whoever proves control of the phone number manages the records registered under it.
     */
    @Modifying
    @Query("UPDATE Patient p SET p.owner = :owner WHERE p.phone = :phone AND p.owner IS NULL")
    int claimUnownedByPhone(String phone, User owner);
}
