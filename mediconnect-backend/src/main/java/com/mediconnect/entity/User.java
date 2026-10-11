package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * A login. Exactly one of two kinds (CHECK below):
 *  - staff login: linked to a staff row, signs in with the staff email + password.
 *    The role is the staff type (ADMIN, DOCTOR, RECEPTIONIST...), read from the staff row, never stored twice.
 *  - patient login: has a phone, signs in with a one-time code sent to it. Role PATIENT.
 *    Owns patient records (self and dependents) through patients.owner_user_id.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users", check = {
        @CheckConstraint(name = "ck_users_kind", constraint = "(staff_id IS NULL) <> (phone IS NULL)"),
        @CheckConstraint(name = "ck_users_staff_password", constraint = "staff_id IS NULL OR password_hash IS NOT NULL")
})
public class User {

    public static final String ROLE_PATIENT = "PATIENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** Deleting the staff member (or doctor) deletes their login with them. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Staff staff;

    /** Patient logins only; normalised like every stored phone (Texts.phone). */
    @Column(name = "phone", unique = true, length = 30)
    private String phone;

    /** Staff logins only (BCrypt, with a {bcrypt} prefix from the delegating encoder). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Expects staff to be loaded when this is a staff login. */
    public String role() {
        return staff != null ? staff.getStaffType() : ROLE_PATIENT;
    }
}
