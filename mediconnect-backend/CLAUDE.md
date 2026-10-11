# MediConnect: Backend

## What this product is
A hospital management web product that is **sold to one hospital**. It is single-tenant by design:
never add multi-hospital / tenant ids. The hospital's admins run their administration from it, and
patients use the public website to find doctors and book appointments online, keep medical
records, and chat with doctors they have actually met.

- This file covers the **Spring Boot backend** (`mediconnect-backend`, package `com.mediconnect`).
- The **frontend** is a separate project (planned, not created yet) and is not wired to this API yet.

## Status (read this before trusting the code)
- **Built:** hospital, departments, facilities, staff, doctors, doctor weekly schedules, patients (full CRUD
  each), public read endpoints, validation, error envelope, pagination, appointments (booking, free-slot
  computation, status state machine, reschedule), auth (staff password login, patient OTP login, JWT access
  token + rotating refresh cookie, roles, record ownership), staff leave (apply/approve, removes a doctor's slots),
  doctors editing their own schedules, encounters (vitals, notes, diagnosis, prescription; draft then final;
  addenda), medical records (file uploads, signed download links, retraction, consent sharing with doctors,
  access log), patient-doctor chat (thread per finalized encounter, 7-day window, WebSocket push, admin oversight
  with access log), pharmacy (medicine catalogue, stock batches with expiry, stock adjustments, prescription queue,
  dispensing earliest-expiry first).
  **No video consultations** (removed on purpose).
- **Confirmed working by the developer in Postman:** hospital create, patient create (only after the
  `patient_uhid_seq` sequence was created by hand), patient update.
- **Smoke-tested by Claude on a fresh database (2026-10-11):** doctor create, verify, schedule create +
  overlap 409, doctor delete cascading its schedules, `/doctors/list` and `/public/doctors/list` sorted by
  `fullName` (public forces verified/ACTIVE), department name case-insensitive 409, patient UHID + duplicate 409.
  Appointments: free slots, booking rules (taken 409, patient busy 409, off-grid/past/too-far/unverified 400),
  reschedule, cancel then rebook the same slot, full status flow, concurrent double booking (one 201, one 409),
  doctor/patient delete refused once they have appointments.
  Auth: 401/403 envelopes, admin bootstrap, staff login, OTP (wrong code, single use, 60 s resend 429), walk-in
  records claimed on patient login, patient/doctor record scoping (404 on other people's records), role limits on
  status changes, refresh rotation + reuse detection, logout, deactivation, password reset, CORS preflight.
  Leave: apply/validation (overlap 409, past/backwards dates 400), pending leave keeps slots, approval empties
  slots and blocks booking/rescheduling onto those days, affected-bookings list + count, visibility (own vs
  admin/reception), cancel restores slots, admin-recorded leave approved at once; doctors edit only own schedule.
  Encounters: open only after check-in, one per appointment (409), role limits (reception none, nurse vitals only,
  other doctors 404), vitals validation, prescription list replaced (orphan rows deleted), finalize needs a
  diagnosis and a started consultation and completes the appointment, FINAL locked (edit/vitals/delete 400),
  addenda only on FINAL, patients see only their FINAL records, appointment with an encounter cannot be deleted.
  Records: upload rules per role, type by magic bytes (renamed file 400), 10 MB limit 413, missing file part 400,
  doctor without consent sees only own uploads (others 404), consent (409 duplicate, 404 for others' patients)
  opens records + other doctors' FINAL encounters (drafts stay hidden), signed link downloads without auth and
  byte-identical, tampered link 401, admin details yes / file no, access log, retract (uploader/admin only),
  revoke closes access again, reception grants on a patient's behalf, doctor's consent list forced to own.
  Chat (Node script, REST + STOMP): thread opens only on finalize (7-day window), inbox per side, other
  patient/doctor 404, admin 403, WS refuses bad/missing token, inactive login, foreign subscriptions and SEND frames,
  live delivery to both parties (and the sender's other sockets) but nobody else, unread counts + mark read,
  cursor paging, blank/2001-char 400, doctor-only close, closed or expired thread read-only (send 400).
  Admin oversight: admin lists/filters all threads and reads messages, send/mark-read 403, no live feed, read markers
  untouched, admin close makes the thread read-only; nurses 403; doctorId filter cannot widen a doctor's inbox.
  Chat access log: parties' own reads not logged, admin READ (each messages call) and CLOSE logged, closedByRole
  ADMIN/DOCTOR, each patient/doctor sees only the log of their own chats (threadId cannot widen it), paging.
  Pharmacy (67 checks + a 12-request burst): catalogue duplicate (any case) 409, roles (doctor/nurse read, patient
  403, only pharmacist/admin write), batch rules (duplicate number 409, expiry today/negative price 400), stock and
  low-stock flag count only unexpired batches, expiry report, prescription lines linked to the catalogue (unknown id
  400), queue shows FINAL prescriptions only (no clinical notes), dispense rules (admin 403, free-text line needs
  medicineId, duplicate/foreign line 400, any line short = whole request 400 and nothing taken), FEFO split over
  batches with correct total, expired batch never used, sale keeps its price after a batch price change, visibility
  (own patient/doctor, others 404, nurse 403), adjustment sign rules and history, delete rules, parallel dispensing
  (12 x 2 units on 19 in stock: exactly 9 succeed, no deadlock, nothing below zero), stock ledger balances.
- **How it was written:** drafted in a Claude chat session and only compile-checked against stand-in
  libraries. Treat it as a reviewed starting point, run it, and fix what breaks.
- **Not built yet:** over-the-counter sales without a prescription, SMS delivery for OTPs (codes are printed in the server log), login rate limiting,
  slot holds, part-day leave, object storage for files (local disk now), virus scanning of uploads, chat attachments
  and read receipts, billing,
  notifications, AI chatbot, frontend integration.

## Stack and running it
Java 17, Spring Boot 4.1.1, Maven, Spring Web, Spring Data JPA (Hibernate), Jakarta Validation, Lombok,
Spring Security 7 (OAuth2 resource server for JWT), Spring WebSocket (STOMP, in-memory broker), PostgreSQL (version 17 recommended). Runs on port 8080.
Environment variables: `DB_NAME` (default `mediconnect`), `DB_USERNAME` (default `postgres`), `DB_PASSWORD`,
`JWT_SECRET` (32+ chars; empty = random per start, tokens die on restart), `ADMIN_EMAIL`
(default `admin@mediconnect.local`), `ADMIN_PASSWORD` (empty = generated and printed once in the log),
`REFRESH_COOKIE_SECURE` (default false for http://localhost; true in production), `STORAGE_DIR` (uploaded
files, default `./data/records`, in .gitignore), `CHAT_WINDOW_DAYS` (default 7). Uploads: PDF/JPEG/PNG, max 10 MB.
CORS allows `http://localhost:5173` with credentials (override with `app.cors.allowed-origins`).
Run: `./mvnw spring-boot:run` (or from the IDE). Never commit passwords.

## Verify first (things I do not know for sure)
1. **Schema: Hibernate `ddl-auto=update`, no Flyway** (decided: personal project, not being sold). Database rules
   live on the entities: `@Table(check = @CheckConstraint(...))`, `uniqueConstraints`, `@OnDelete(CASCADE)` on
   schedules. `schema.sql` (runs after Hibernate on every startup, so only re-runnable `IF NOT EXISTS` statements)
   holds what annotations cannot: `patient_uhid_seq` and the case-insensitive unique indexes on department/facility
   name and staff email. `update` never alters existing tables, so after changing a rule on an entity the
   developer must recreate the database. No placeholder hospital row (POST /hospital creates it; CHECK
   `hospital_id = 1` keeps it to one row).
2. List queries use `CAST(:param AS String) IS NULL OR ...` so PostgreSQL can type null parameters
   (confirmed working on Hibernate 7 / PostgreSQL 16).

## Package layout and conventions (follow them)
Modelled on the developer's earlier CLRG project.
- `controller/`: thin. `@RestController @RequiredArgsConstructor`, every method returns the service's
  `ResponseEntity<Object>`. Method order in a controller: POST, GET, PUT, DELETE.
- `service/`: all business rules. Returns `ResponseEntity<Object>` built with `ResponseHandler`.
  `@Transactional` on writes, `@Transactional(readOnly = true)` on reads.
- `repository/`: Spring Data interfaces returning **entities** (not DTO projections). JPQL `@Query` for
  filtered lists. Lazy associations are loaded with `JOIN FETCH` because `open-in-view` is off. Parameter names
  are bound by compiler `-parameters` (no `@Param`). `countQuery` is written out where `JOIN FETCH` is used.
- `dto/<module>/`: Java **records**. `XRequest` (Jakarta validation with literal messages), `XResponse`
  (static `from(entity)`), `XListResponse` (lighter row for tables/public pages), `XFiltersRequest` (all fields
  nullable = "do not filter"). Shared: `dto/ValidationRegex`, `dto/ActiveStatusRequest`.
- `entity/`: Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, explicit `@Column`
  names, `@GeneratedValue(IDENTITY)`. **No auditing fields** (decided: not needed now). Enumerated values
  (gender, staffType, status, bloodGroup, appointment status) are plain **Strings**, validated by `@Pattern` in the DTOs.
  Only `dayOfWeek` uses a Java enum (`java.time.DayOfWeek`).
- `response/`: `ResponseHandler`, `Status`, `PageResponse`. Every response is
  `{ "data": ..., "pagination": {page,size,totalElements,totalPages,last}, "status": {code, message} }`
  (`data` omitted when null, `pagination` only on lists).
- `exception/`: `NotFoundException` 404, `AlreadyExistException` 409, `BadRequestException` 400,
  `UnauthorizedException` 401, `ForbiddenException` 403 (service rules), `TooManyRequestsException` 429, and
  `GlobalExceptionHandler` (validation errors give `data` = {field: message}; unreadable JSON 400;
  `DataIntegrityViolationException` 409). Deliberately **no catch-all Exception handler**.
- `config/`: `MessageUtils`, `CorsConfig`, `WebSocketConfig` (STOMP endpoint `/ws`, broker `/queue`). `helper/PageableBuilder`, `helper/HospitalZone` (hospital timezone,
  default Asia/Kolkata before the hospital exists). `util/Texts`, `util/Hashes` (SHA-256).
- `security/`: `SecurityConfig` (stateless, CSRF off, 401/403 envelopes), `JwtConfig` (HS256 encoder/decoder),
  `TokenService`, `CurrentUser` (id/role/doctorId from the token), `OtpService` (in-memory codes),
  `AdminBootstrap`, `Access` (the `@PreAuthorize` expressions), `DownloadLinks` (HMAC-signed 5-minute file links),
  `StompAuthInterceptor` (WebSocket auth: token in the STOMP CONNECT frame, own `/user/queue/**` only, no SEND).
- `storage/`: `FileStorage` interface + `LocalFileStorage` (random UUID file names under `STORAGE_DIR`). Swap the
  implementation for S3/MinIO without touching services.
- **Authorization:** endpoint roles with `@PreAuthorize(Access.X)` on controller methods (class-level default
  where most methods share it). Record ownership is checked in the services through `CurrentUser`: a record the
  caller may not see is a **404**, never 403, so ids reveal nothing.
- **Messages:** all user-facing text lives in `messages.properties`, read through
  `MessageUtils.getMessage(key, args)`. Pass ids and numbers as `String.valueOf(...)` (MessageFormat would
  print 1,234).
- **Lists:** `POST /{resource}/list?page=0&size=10&sort=field,asc|desc` with an optional filter body.
  Page size is clamped to 1-100. Sort fields are whitelisted per controller (`PageableBuilder`), ties
  broken by id.
- **Deletes:** `repository.delete(x); repository.flush();` inside `@Transactional`, so a constraint failure
  surfaces in the transaction and reaches the global handler (deletes are otherwise only sent at commit).
- **PUT replaces the whole record.** Optional fields left out become null or their default (documented in each
  request DTO's Javadoc).
- **Normalisation before storing or comparing** (`Texts`): trim, blank to null, emails lower-cased, phones
  without spaces/hyphens, language lists trimmed and de-duplicated.
- **Business tables get full CRUD**, but skip endpoints that are genuinely unnecessary (developer's decision,
  2026-10-11). Confirmed exceptions: `refresh_tokens` has no CRUD API (security data, managed only by /auth);
  `/users` updates are `/password` and `/status` only (nothing else on a login is editable). When unsure, ask.

## Domain model (current tables)
- `hospitals`: exactly one row, id fixed at 1. POST returns 409 while it exists; DELETE then POST starts over.
  Has a timezone (IANA id, default `Asia/Kolkata`) used for schedule logic.
- `departments`, `facilities`: name unique ignoring case, `active` flag.
- `staff`: every employee, doctors included. staffCode and email unique. `staffType`: DOCTOR, NURSE,
  RECEPTIONIST, PHARMACIST, LAB_TECHNICIAN, ADMIN, OTHER. `status`: ACTIVE or INACTIVE ("on leave" is
  derived from leave data later, never stored here). Optional department.
- `doctors`: professional data, 1:1 with a staff row. specialization and qualifications are free-text
  columns, `languages` is `text[]`, fee, `verified`. Unique (registrationCouncil, registrationNumber).
  Name, phone, email and department live on the linked staff row.
- `doctor_schedules`: weekly template per doctor (day, local start/end time, slot minutes, validFrom/validTo).
  Slots are generated from this at read time and are **not stored**.
- `appointments`: patient, doctor, `slot_start`/`slot_end` (UTC `timestamptz`; API uses local `date` + `startTime`
  converted with the hospital timezone), `status`, `reason`, `cancellation_reason`, `rescheduled_from_id`.
  Status: REQUESTED, CONFIRMED, CHECKED_IN, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED.
  Partial unique index (doctor_id, slot_start) WHERE status NOT IN (CANCELLED, RESCHEDULED), in `schema.sql`.
- `patients`: name, date of birth, gender, phone, optional email/blood group/address/emergency contact, `active`.
  `uhid` is the hospital-facing id, `UH` + 8 digits from sequence `patient_uhid_seq`.
  Phone and email are intentionally **not unique** (family members share them).
  `owner_user_id` (nullable, ON DELETE SET NULL): the patient login managing the record (self or dependents).
- `users`: a login. Either a staff login (`staff_id`, BCrypt `password_hash`; role = the staff's `staffType`, not
  stored) or a patient login (`phone`; role PATIENT). CHECK enforces exactly one kind. Deleted with its staff row.
- `refresh_tokens`: SHA-256 hash of each refresh token, `expires_at`, `rotated`. Deleted with the user.
- `staff_leaves`: staff, `leave_type` (CASUAL, SICK, EARNED, OTHER), `start_date`/`end_date` (whole days,
  inclusive, hospital-local), `reason`, `status` (PENDING, APPROVED, REJECTED, CANCELLED), `review_note`.
  On staff, not doctors, so it works for every employee. Deleted with the staff row.
- `encounters`: the clinical record of one appointment (unique `appointment_id`, no ON DELETE, so an appointment
  with a record cannot be deleted). `status` DRAFT or FINAL (CHECK: FINAL needs `finalized_at` and `diagnosis`).
  Vitals (bp, pulse, temperature, SpO2, weight, height; all optional), chief complaint, clinical notes,
  diagnosis, advice, follow-up date, `finalized_at`.
- `prescription_items`: medicines of an encounter (name, dosage, frequency e.g. "1-0-1", duration days,
  instructions), optional `medicine_id` (catalogue link; the name stays required). No own endpoints: replaced
  through the encounter PUT.
- `encounter_addenda`: text + `added_at`, appended to FINAL encounters. Never edited or deleted.
- `medical_records`: patient, title, `record_type` (LAB_REPORT, IMAGING, PRESCRIPTION, DISCHARGE_SUMMARY, OTHER),
  `record_date`, notes, file metadata (original name, detected content type, size, `storage_key`), uploader,
  `uploaded_at`, `status` ACTIVE or RETRACTED (+ reason). Append-only: no update, no delete.
- `record_consents`: patient -> doctor, `granted_at`, `expires_at`, `revoked_at`. Active = not revoked and not
  expired (computed). Never deleted.
- `record_access_logs`: record, user, `action` VIEW or DOWNLOAD, `accessed_at`. Insert-only.
- `chat_threads`: one per encounter (unique `encounter_id`), `opened_at` (= finalized_at), `closes_at` (+ window),
  `closed_at` (doctor closed early), `last_activity_at` (inbox sort), `patient_last_read_id`/`doctor_last_read_id`
  (read markers: unread = the other side's messages with a higher id), `closed_by_role` (DOCTOR/ADMIN).
  Open = not closed and before closes_at.
- `chat_messages`: thread, `sender_user_id` (ON DELETE SET NULL), `sender_role` PATIENT or DOCTOR, `body` (text,
  max 2000), `sent_at`. Never edited or deleted (a teleconsultation chat is part of the medical record).
- `chat_access_logs`: thread, user (ON DELETE SET NULL), `action` READ or CLOSE, `accessed_at`. Admin accesses
  only (the parties' own reads are not logged). Insert-only.
- `medicines`: catalogue. name, generic name, `form` (TABLET, CAPSULE, SYRUP, SUSPENSION, INJECTION, OINTMENT,
  CREAM, DROPS, INHALER, POWDER, OTHER), strength, manufacturer, `reorder_level`, `active`. Unique ignoring case on
  name + form + strength (index in `schema.sql`). Stock is not stored here: it is summed from batches.
- `medicine_batches`: one delivery. medicine, `batch_number` (unique per medicine), `expiry_date` (last usable day),
  `quantity_received`, `quantity_on_hand` (CHECK >= 0), `unit_price` (per unit), `received_at`, received by user.
  Usable = expiry today or later (hospital date).
- `stock_adjustments`: batch, `quantity_change` (non-zero), `quantity_after`, `reason` (DAMAGED, EXPIRED, RETURNED,
  CORRECTION, OTHER), note, user, `adjusted_at`. Insert-only.
- `dispensations`: encounter (several allowed: partial fills), dispensed by user, `dispensed_at`, `total_amount`,
  notes. Never edited or deleted.
- `dispensation_items`: dispensation, prescription item, medicine, batch, quantity, `unit_price` (copied from the
  batch at sale time), `line_total`. One prescription line can span several batches.

## Business rules (do not break these)
- A doctor is a Staff row plus a Doctor row, always written together in one transaction.
  `StaffController/StaffService` refuses to edit or delete DOCTOR rows, and the staff request pattern rejects
  `staffType = DOCTOR`. Doctors are only managed through `/doctors`.
- Changing a doctor's registration number or council resets `verified` to false (and the response says so).
  Verification is its own endpoint, not part of editing a profile.
- Public endpoints (`/public/**`) show only **verified and ACTIVE** doctors, forced server-side whatever the body
  says, and use `DoctorListResponse` which has no phone/email/registration/date of birth.
- Schedule rules: end after start, `validTo` not before `validFrom`, window length must be an exact multiple of
  the slot length, and no overlap with another schedule of the same doctor on the same weekday during overlapping
  validity. Schedules are always addressed through their doctor
  (`/doctors/{doctorId}/schedules/{id}`), and a schedule of another doctor is a 404.
- Patient duplicate check is name (any case) + date of birth + normalised phone, giving 409.
  Patient `uhid` never changes. Deactivate with `PUT /patients/{id}/status`; delete is refused by the database
  once other tables reference the patient.
- Booking (`AppointmentService`): the slot must be one the doctor's schedule produces on that date, start in the
  future, and fall within today..today+30 days; doctor verified and ACTIVE; patient active; no overlapping active
  booking for the doctor or the patient (409). New bookings are CONFIRMED straight away. "Active" = any status
  except CANCELLED and RESCHEDULED, so a cancelled slot can be booked again.
- Status moves: REQUESTED to CONFIRMED/CANCELLED; CONFIRMED to CHECKED_IN/CANCELLED/NO_SHOW; CHECKED_IN to
  IN_CONSULTATION; IN_CONSULTATION to COMPLETED; the rest are final. Cancel only before the start, NO_SHOW only
  after it, check-in only on the appointment day. Reschedule (same doctor, REQUESTED/CONFIRMED, before the start)
  marks the old booking RESCHEDULED and creates a new CONFIRMED one pointing back to it. PUT edits only `reason`.
- Doctors and patients with appointments cannot be deleted (FK, 409); deactivate them instead.
- Department delete is refused while staff are assigned to it.
- Hospital timezone must be a real IANA region id (not an offset).
- Auth: staff sign in with staff email + password; patients with phone + 6-digit OTP (5 min, single use, 5 tries,
  resend after 60 s), and the patient login is created on first sign-in. On every patient sign-in, unowned
  patient records with that phone become owned by the login. Access token 15 min (claims: sub, role, staffId,
  doctorId); refresh token 7 days in an httpOnly, SameSite=Strict cookie on path /auth, rotated on every refresh.
  A rotated token presented again = theft: all the user's refresh tokens are deleted. Logout deletes this device's
  token; password change/reset and deactivation delete all of them. Inactive users or INACTIVE staff cannot sign in.
  Staff of type OTHER get no login. The admin cannot deactivate or delete their own login.
- Record scope: a patient login sees/edits only the patients it owns and their appointments, and books only for
  them; a doctor sees only their own appointments (list filter forced) and only patients they have an
  appointment with. Status changes by role: PATIENT only CANCELLED; DOCTOR only IN_CONSULTATION, COMPLETED,
  NO_SHOW; other staff any (still subject to the transition table).
- Leave (`LeaveService`): staff apply for themselves (PENDING; start not in the past); an admin approves or
  rejects; leave an admin records (optionally for another staffId) is APPROVED at once and may be backdated. No
  overlap with the person's other PENDING/APPROVED leave (409). PUT only while PENDING. Cancel: the person (pending
  any time, approved only before it starts) or an admin (any time). Admins and reception see all leave; other
  staff only their own (others are 404). APPROVED leave of a doctor: no slots that day and booking/rescheduling
  onto it is refused. Existing bookings are NOT auto-cancelled (no notifications yet); approval reports how many
  are affected and `GET /leaves/{id}/appointments` lists them for reception to reschedule.
- Doctors may create/update/delete only their own schedules (403 otherwise); admins any.
- Encounters (`EncounterService`): opened (DRAFT) by a nurse or the appointment's doctor once the appointment is
  CHECKED_IN or IN_CONSULTATION; one per appointment. Vitals: nurse or that doctor (own endpoint, so the doctor's
  PUT never wipes them). Notes/diagnosis/advice/follow-up (after the visit date)/prescription: that doctor only;
  PUT replaces them and the whole prescription. Finalize: diagnosis required, appointment must be IN_CONSULTATION
  (moves it to COMPLETED) or already COMPLETED; then FINAL is locked for good: corrections are addenda (FINAL
  only). Only DRAFTs can be deleted. Read access: admins and nurses all; a doctor their own, plus other doctors'
  FINAL encounters of a patient whose active consent they hold (never other doctors' drafts); a patient only FINAL
  encounters of patients they own; reception none.
- Records (`RecordService`): upload = patient (own patients), doctor (patients they have an appointment with),
  other staff (any patient). File type decided by magic bytes (PDF/JPEG/PNG), never the name or header. Read
  (details + file) = owning patient login, the uploader, or a doctor with active consent; admins get details but
  never the file; everyone else 404. Lists: patient own; doctor all of a consenting patient (with patientId),
  else own uploads; admin all; other staff own uploads. Files only through `GET /records/{id}/download-url`
  (5-minute HMAC link, bound to record + user) then `GET /files/{token}` (open, inline, no-store). Every detail
  view and download is logged; patients and admins read the log. Retract: uploader or admin, reason required.
- Consent (`ConsentService`): granted/revoked by the owning patient login or reception/admin on their behalf;
  default 30 days (1-365); one active grant per patient-doctor pair (409). Doctors list grants given to them.
- Pharmacy catalogue and stock (`MedicineService`, `BatchService`): pharmacist/admin write, all staff read the
  catalogue. Stock is received as batches (expiry must be after today); batch label data (number, expiry, price)
  can be corrected, quantities only change by dispensing and adjustments. Adjustment sign rules: DAMAGED/EXPIRED
  remove, RETURNED adds, OTHER needs a note, never below zero. A batch can be deleted only while untouched; a
  medicine only while it has no batches and no prescription links (deactivate otherwise).
- Dispensing (`DispensationService`): pharmacist only (not admins). FINAL encounters only, each prescription line
  at most once per request, a free-text line needs a medicineId. Takes the earliest-expiring usable batches first
  (FEFO), all or nothing per request. Batches are locked FOR UPDATE in ascending medicine id order (no double sale,
  no deadlock). The queue shows medicines only, never clinical notes. Read: pharmacist/admin all, a doctor their own
  encounters, a patient their own patients; others 404. Returns go back as RETURNED adjustments.
- Chat (`ChatService`): finalizing an encounter opens its thread (same transaction) for `app.chat.window-days`
  (7). Two parties write: the encounter's doctor and the patient login owning the patient record; another doctor
  or patient gets 404, other staff 403. Admins read every thread (safety oversight, developer's decision 2026-10-11)
  and may close one, but never send, mark read or get the live push; their unreadCount is always 0. Every admin
  messages read and close is logged in `chat_access_logs`, readable by both parties for their own chats. Messages are sent over REST, then pushed AFTER COMMIT
  (`ChatNotifier`) to `/user/queue/chat` of both logins. Text only. The doctor may close early; a closed or
  expired thread stays readable but sending is 400. Messages paged by cursor (`before` = message id), not page number.

## API (`/public/**` and `/auth/login|otp|refresh|logout` are open; everything else needs a Bearer token)
- `/auth`: POST `/login`, POST `/otp/request`, POST `/otp/verify`, POST `/refresh`, POST `/logout`, GET `/me`,
  PUT `/password`.
- `/users` (ADMIN): POST `/list`, GET `/{id}`, POST, PUT `/{id}/password`, PUT `/{id}/status`, DELETE `/{id}`.
- `/leaves` (any staff; rules in the service): POST `/list`, GET `/{id}`, GET `/{id}/appointments`
  (ADMIN/RECEPTIONIST), POST, PUT `/{id}`, PUT `/{id}/status`, DELETE `/{id}` (ADMIN).
- `/encounters`: POST `/list`, GET `/{id}` (ADMIN/NURSE/DOCTOR/PATIENT), POST (NURSE/DOCTOR),
  POST `/{id}/addenda` (DOCTOR), PUT `/{id}` (DOCTOR), PUT `/{id}/vitals` (NURSE/DOCTOR), PUT `/{id}/finalize`
  (DOCTOR), DELETE `/{id}` (ADMIN/DOCTOR, drafts only).
- `/records` (any signed-in user; rules in the service): POST `/list`, POST `/access-log` (ADMIN/PATIENT),
  GET `/{id}`, GET `/{id}/download-url`, POST (multipart: file + patientId, title, recordType, recordDate, notes),
  PUT `/{id}/retract`. No PUT/DELETE by design. `GET /files/{token}` serves the file (open; signed token).
- `/consents`: POST `/list` (ADMIN/RECEPTIONIST/DOCTOR/PATIENT), POST and PUT `/{id}/revoke`
  (ADMIN/RECEPTIONIST/PATIENT).
- `/chats` (ADMIN/DOCTOR/PATIENT): POST `/list` (filters encounterId, patientId, doctorId (admin only), openOnly;
  sort lastActivity), POST `/{id}/messages` (DOCTOR/PATIENT), GET `/{id}`, GET `/{id}/messages?before=&size=30`,
  PUT `/{id}/read` (DOCTOR/PATIENT), PUT `/{id}/close` (ADMIN/DOCTOR), POST `/access-log` (filter threadId). No create (threads come from finalize), no edit/delete. WebSocket: connect to `ws://host/ws` (STOMP,
  header `Authorization: Bearer <token>` on CONNECT), subscribe `/user/queue/chat`; payload = the message JSON.
- Roles: hospital/departments/facilities/doctors: read = any staff, write = ADMIN. Schedules: read = any staff,
  write = ADMIN or the doctor themselves. `/staff`: ADMIN.
  `/patients`: list = ADMIN/RECEPTIONIST/NURSE/PATIENT, get = those + DOCTOR, create/update =
  ADMIN/RECEPTIONIST/PATIENT, status = ADMIN/RECEPTIONIST, delete = ADMIN. `/appointments`: list/get/status =
  ADMIN/RECEPTIONIST/NURSE/DOCTOR/PATIENT, create/update/reschedule = ADMIN/RECEPTIONIST/PATIENT, delete = ADMIN.
- `/medicines` (read: any staff; write: ADMIN/PHARMACIST): POST `/list` (filters name, form, active, lowStock),
  GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}`.
- `/batches` (ADMIN/PHARMACIST): POST `/list` (filters medicineId, expiringWithinDays, inStockOnly), POST,
  POST `/{id}/adjustments`, GET `/{id}`, GET `/{id}/adjustments`, PUT `/{id}` (label data), DELETE `/{id}`.
- `/dispensations`: POST `/queue` (ADMIN/PHARMACIST; filters patientId, pendingOnly default true), POST `/list`
  and GET `/{id}` (ADMIN/PHARMACIST/DOCTOR/PATIENT), POST (PHARMACIST). No PUT/DELETE by design.
- `/hospital`: POST, GET, PUT, DELETE (single record).
- `/departments`, `/facilities`: POST `/list`, GET `/active`, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}`.
- `/staff`: POST `/list`, GET `/{id}`, POST, PUT `/{id}`, DELETE `/{id}` (non-doctors only).
- `/doctors`: POST `/list`, GET `/{id}`, POST, PUT `/{id}`, PUT `/{id}/verification`, PUT `/{id}/status`,
  DELETE `/{id}`.
- `/doctors/{doctorId}/schedules`: GET, POST, PUT `/{scheduleId}`, DELETE `/{scheduleId}`.
- `/patients`: POST `/list`, GET `/{id}`, POST, PUT `/{id}`, PUT `/{id}/status`, DELETE `/{id}`.
- `/appointments`: POST `/list` (filters doctorId, patientId, status, dateFrom, dateTo), GET `/{id}`, POST,
  PUT `/{id}`, PUT `/{id}/status`, PUT `/{id}/reschedule`, DELETE `/{id}`.
- `/public`: GET `/hospital`, GET `/departments`, GET `/facilities`, POST `/doctors/list`,
  GET `/doctors/{id}/slots?date=YYYY-MM-DD`.

## Where this is heading (design intent for the next modules)
- **Auth (next steps):** an SMS provider for OTPs, rate limiting on /auth/login and /auth/otp.
- **Appointments (next steps):** slot holds only if online payment is added. Keep Appointment (the booking)
  separate from Encounter (what happened).
- **Leave (next steps):** part-day leave; notify patients and offer rebooking once notifications exist.
- **Encounters (next steps):** printable prescription.
- **Medical records (next steps):** object storage (S3/MinIO) behind `FileStorage`, virus scanning, thumbnails.
- **Chat (next steps):** attachments (through /records), read receipts and typing indicators over the socket,
  notifications for offline users, a broker relay (RabbitMQ/Redis) if the app ever runs on several servers.
- **AI chatbot:** FAQ over hospital data first (RAG with pgvector), then booking via tool calls with user
  confirmation, symptom-to-specialty triage with hard emergency rules. No diagnosis. Use synthetic data only.
- **Pharmacy (next steps):** over-the-counter sales, purchase orders and suppliers, a check that a batch does not
  expire before the course ends, printable receipt, billing integration.
- **Other:** billing, notifications. India-specific:
  UHID, ABDM/ABHA later, Telemedicine Practice Guidelines apply to chat, DPDP Act for personal data.
  No video consultations (decided).

## How to work with the developer
- Explain the reasoning and the trade-offs behind decisions in a way they can defend in an interview.
- Direct, practical answers. Copy-paste-ready code and payloads.
- Do not narrow scope on your own (for example by dropping CRUD operations for a table). Ask first.
- Time for testing is limited: test the features the website needs, not every CRUD endpoint in isolation.
- **Claude does the testing** (developer's decision, 2026-10-11: they do not test in Postman). After every feature,
  run an end-to-end test yourself: create a scratch database (e.g. `mc_<feature>_test`; local PostgreSQL 16 at
  `D:\PostgreSQL`, tools in `D:\PostgreSQLin`), start the app on port 8099 with `DB_NAME` pointing at it, drive
  it with a Node script (Node 22 has `fetch` and `WebSocket` built in; OTP codes are read from the app log), then
  stop the app, drop the scratch database and delete the scratch files. Report what was tested and the results.
  **Never touch the developer's real `mediconnect` database.**
- Only list steps the developer must do themselves (reload Maven, recreate the database after an entity rule
  change, set env vars). They use **VS Code** (not IntelliJ) on Windows; env vars for running from the IDE live in
  `.vscode/launch.json` (gitignored).
- If Postman steps are ever asked for: method, full URL and JSON body, one request at a time, in the order
  POST, GET, PUT, DELETE.
