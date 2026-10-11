package com.mediconnect.security;

/**
 * @PreAuthorize expressions shared by the controllers, so "who may call this" reads the same everywhere.
 * Roles are staff types (ADMIN, DOCTOR, NURSE, RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN) or PATIENT.
 * These only decide who may call an endpoint; which RECORDS a patient or doctor may touch is checked in the
 * services (a patient only the patient records they own, a doctor only their own appointments and patients).
 */
public final class Access {

    private Access() {}

    public static final String ADMIN = "hasRole('ADMIN')";

    /** Any signed-in user; the service decides which records they reach. */
    public static final String SIGNED_IN = "isAuthenticated()";

    /** Who manages consent: the patient, or the desk on the patient's behalf. Doctors only see grants to them. */
    public static final String CONSENT_VIEWERS = "hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR', 'PATIENT')";

    public static final String ADMIN_OR_PATIENT = "hasAnyRole('ADMIN', 'PATIENT')";

    /** Every employee login. */
    public static final String STAFF =
            "hasAnyRole('ADMIN', 'DOCTOR', 'NURSE', 'RECEPTIONIST', 'PHARMACIST', 'LAB_TECHNICIAN')";

    /** Admins, or doctors acting on their own data (e.g. their own schedule; checked in the service). */
    public static final String ADMIN_OR_DOCTOR = "hasAnyRole('ADMIN', 'DOCTOR')";

    /** Registration and booking desk. */
    public static final String FRONT_DESK = "hasAnyRole('ADMIN', 'RECEPTIONIST')";

    /** Front desk, or a patient acting on their own records. */
    public static final String FRONT_DESK_OR_PATIENT = "hasAnyRole('ADMIN', 'RECEPTIONIST', 'PATIENT')";

    /** Searching the patient register. Patients only see their own records; doctors use their appointment list. */
    public static final String PATIENT_SEARCH = "hasAnyRole('ADMIN', 'RECEPTIONIST', 'NURSE', 'PATIENT')";

    public static final String DOCTOR = "hasRole('DOCTOR')";

    /** The two parties of a chat: only they write in it or move their read marker. */
    public static final String CHAT_PARTICIPANTS = "hasAnyRole('DOCTOR', 'PATIENT')";

    /** Who may read chats: the two parties, plus admins for safety oversight (read and close, never write). */
    public static final String CHAT_VIEWERS = "hasAnyRole('ADMIN', 'DOCTOR', 'PATIENT')";

    /** Pharmacy stock and catalogue management. */
    public static final String PHARMACY = "hasAnyRole('ADMIN', 'PHARMACIST')";

    /** Handing medicines over is the pharmacist's licensed job, so not even admins dispense. */
    public static final String PHARMACIST = "hasRole('PHARMACIST')";

    /** Who may read dispensations. Doctors see their own encounters' and patients their own (checked in the service). */
    public static final String DISPENSATION_VIEWERS = "hasAnyRole('ADMIN', 'PHARMACIST', 'DOCTOR', 'PATIENT')";

    /** Hands-on clinical staff (vitals, opening an encounter). */
    public static final String CLINICIANS = "hasAnyRole('NURSE', 'DOCTOR')";

    /** Who may read clinical records. Reception is deliberately absent: booking staff do not need clinical notes. */
    public static final String CLINICAL_RECORDS = "hasAnyRole('ADMIN', 'NURSE', 'DOCTOR', 'PATIENT')";

    /** Everyone involved in care, plus the patient. Doctors are limited to their own patients/appointments. */
    public static final String CARE_TEAM_OR_PATIENT =
            "hasAnyRole('ADMIN', 'RECEPTIONIST', 'NURSE', 'DOCTOR', 'PATIENT')";
}
