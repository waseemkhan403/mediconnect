package com.mediconnect.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Singleton: this product serves exactly one hospital. The row always has id = 1
 * (enforced by a CHECK constraint in the database) and is created through POST /hospital.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "hospitals",
        check = @CheckConstraint(name = "ck_hospitals_singleton", constraint = "hospital_id = 1"))
public class Hospital {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "hospital_id")
    @Builder.Default
    private Long id = SINGLETON_ID;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "about", columnDefinition = "text")
    private String about;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 100)
    @Builder.Default
    private String country = "India";

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "emergency_phone", length = 30)
    private String emergencyPhone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "website", length = 200)
    private String website;

    /** Free text for display, e.g. "OPD: Mon-Sat 9 AM - 5 PM | Emergency: 24x7". Bookable time comes from doctor schedules. */
    @Column(name = "opening_hours", length = 300)
    private String openingHours;

    /** IANA zone id. All slot/schedule logic interprets local times in this zone. */
    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Asia/Kolkata";
}
