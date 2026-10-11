package com.mediconnect.dto;

/**
 * Allowed-value patterns shared by request DTOs. They mirror the CHECK constraints in the migrations,
 * so bad input is rejected with a clear 400 message before it can reach the database.
 */
public final class ValidationRegex {

    private ValidationRegex() {}

    /** 7-20 chars: optional leading +, digits, spaces, hyphens (e.g. +91 98765 43210, 040-23456789). */
    public static final String PHONE = "\\+?[0-9][0-9 \\-]{5,18}[0-9]";

    public static final String GENDER = "MALE|FEMALE|OTHER";

    /** DOCTOR is excluded on purpose: doctors are created through the doctor API, which creates the staff row too. */
    public static final String STAFF_TYPE_NON_DOCTOR =
            "NURSE|RECEPTIONIST|PHARMACIST|LAB_TECHNICIAN|ADMIN|OTHER";

    public static final String STAFF_STATUS = "ACTIVE|INACTIVE";

    public static final String BLOOD_GROUP =
            "A_POSITIVE|A_NEGATIVE|B_POSITIVE|B_NEGATIVE|AB_POSITIVE|AB_NEGATIVE|O_POSITIVE|O_NEGATIVE";

    /**
     * Statuses a client may move an appointment to. REQUESTED is only a starting state, and RESCHEDULED is set
     * by the reschedule endpoint, so neither is accepted here.
     */
    public static final String APPOINTMENT_STATUS_CHANGE =
            "CONFIRMED|CHECKED_IN|IN_CONSULTATION|COMPLETED|CANCELLED|NO_SHOW";

    public static final String LEAVE_TYPE = "CASUAL|SICK|EARNED|OTHER";

    public static final String RECORD_TYPE = "LAB_REPORT|IMAGING|PRESCRIPTION|DISCHARGE_SUMMARY|OTHER";

    /** PENDING is only a starting state. */
    public static final String LEAVE_STATUS_CHANGE = "APPROVED|REJECTED|CANCELLED";

    public static final String MEDICINE_FORM =
            "TABLET|CAPSULE|SYRUP|SUSPENSION|INJECTION|OINTMENT|CREAM|DROPS|INHALER|POWDER|OTHER";

    /** DAMAGED and EXPIRED remove stock, RETURNED adds it, CORRECTION and OTHER go either way. */
    public static final String STOCK_ADJUSTMENT_REASON = "DAMAGED|EXPIRED|RETURNED|CORRECTION|OTHER";
}
