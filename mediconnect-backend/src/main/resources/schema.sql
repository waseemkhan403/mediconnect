-- Runs after Hibernate creates/updates the tables, on every startup, so every statement must be re-runnable.
-- Only for what entity annotations cannot express.

-- PatientService formats nextval as the UHID: UH + 8 digits.
CREATE SEQUENCE IF NOT EXISTS patient_uhid_seq START WITH 1 INCREMENT BY 1;

-- Case-insensitive uniqueness ("Cardiology" = "cardiology"). The services check first; these stop races.
CREATE UNIQUE INDEX IF NOT EXISTS uq_departments_name_ci ON departments (LOWER(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_facilities_name_ci ON facilities (LOWER(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_email_ci ON staff (LOWER(email));

-- No double booking: one active booking per doctor per slot start. Partial, so CANCELLED and RESCHEDULED
-- bookings do not block the slot from being booked again.
CREATE UNIQUE INDEX IF NOT EXISTS uq_appointments_doctor_slot ON appointments (doctor_id, slot_start)
    WHERE status NOT IN ('CANCELLED', 'RESCHEDULED');
CREATE INDEX IF NOT EXISTS ix_appointments_patient ON appointments (patient_id, slot_start);

-- A medicine is identified by name + form + strength, ignoring case ("Dolo 650" TABLET "650 mg").
CREATE UNIQUE INDEX IF NOT EXISTS uq_medicines_identity ON medicines (LOWER(name), form, LOWER(COALESCE(strength, '')));
-- Dispensing reads the usable batches of a medicine, earliest expiry first.
CREATE INDEX IF NOT EXISTS ix_batches_medicine_expiry ON medicine_batches (medicine_id, expiry_date);
