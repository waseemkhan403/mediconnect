package com.mediconnect.service;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.dto.hospital.HospitalRequest;
import com.mediconnect.dto.hospital.HospitalResponse;
import com.mediconnect.entity.Hospital;
import com.mediconnect.exception.AlreadyExistException;
import com.mediconnect.exception.BadRequestException;
import com.mediconnect.exception.NotFoundException;
import com.mediconnect.repository.HospitalRepository;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import com.mediconnect.util.Texts;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * Full CRUD on the hospital, but this product serves ONE hospital: the table can hold at most one row
 * (id is fixed at 1, enforced by a CHECK constraint). So POST is refused while a hospital exists,
 * and DELETE followed by POST lets you start over.
 */
@Service
@RequiredArgsConstructor
public class HospitalService {

    private static final String DEFAULT_COUNTRY = "India";
    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    private final HospitalRepository hospitalRepository;
    private final MessageUtils messageUtils;

    // -------------------------------------------------------------------------
    // CREATE (only when no hospital exists yet)
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> addHospital(HospitalRequest request) {
        if (hospitalRepository.existsById(Hospital.SINGLETON_ID)) {
            throw new AlreadyExistException(messageUtils.getMessage("hospital.exists"));
        }
        Hospital hospital = new Hospital();
        hospital.setId(Hospital.SINGLETON_ID);
        Hospital saved = hospitalRepository.save(fill(hospital, request));
        return ResponseHandler.generateResponse(
                HttpStatus.CREATED,
                HospitalResponse.from(saved),
                new Status(201, messageUtils.getMessage("hospital.add.success")));
    }

    // -------------------------------------------------------------------------
    // GET (there is only one hospital)
    // -------------------------------------------------------------------------
    @Transactional(readOnly = true)
    public ResponseEntity<Object> getHospital() {
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                HospitalResponse.from(load()),
                new Status(200, messageUtils.getMessage("hospital.get.success")));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> updateHospital(HospitalRequest request) {
        Hospital saved = hospitalRepository.save(fill(load(), request));
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                HospitalResponse.from(saved),
                new Status(200, messageUtils.getMessage("hospital.update.success")));
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------
    @Transactional
    public ResponseEntity<Object> deleteHospital() {
        Hospital hospital = load();
        hospitalRepository.delete(hospital);
        // Flush so a constraint failure surfaces inside this transaction and reaches GlobalExceptionHandler.
        hospitalRepository.flush();
        return ResponseHandler.generateResponse(
                HttpStatus.OK,
                null,
                new Status(200, messageUtils.getMessage("hospital.delete.success")));
    }

    /** Validates the timezone and copies the request onto the entity (shared by create and update). */
    private Hospital fill(Hospital hospital, HospitalRequest request) {
        String timezone = Texts.clean(request.timezone());
        if (timezone == null) {
            timezone = DEFAULT_TIMEZONE;
        }
        // Only IANA region ids (Asia/Kolkata); ZoneId.of alone would also accept offsets like "+05:30".
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException(messageUtils.getMessage("hospital.timezone.invalid", timezone));
        }

        hospital.setName(request.name().trim());
        hospital.setAbout(Texts.clean(request.about()));
        hospital.setAddressLine1(Texts.clean(request.addressLine1()));
        hospital.setAddressLine2(Texts.clean(request.addressLine2()));
        hospital.setCity(Texts.clean(request.city()));
        hospital.setState(Texts.clean(request.state()));
        hospital.setPostalCode(Texts.clean(request.postalCode()));
        String country = Texts.clean(request.country());
        hospital.setCountry(country != null ? country : DEFAULT_COUNTRY);
        // Hospital phones are shown to patients, so keep the formatting the admin typed.
        hospital.setPhone(Texts.clean(request.phone()));
        hospital.setEmergencyPhone(Texts.clean(request.emergencyPhone()));
        hospital.setEmail(Texts.email(request.email()));
        hospital.setWebsite(Texts.clean(request.website()));
        hospital.setOpeningHours(Texts.clean(request.openingHours()));
        hospital.setTimezone(timezone);
        return hospital;
    }

    private Hospital load() {
        return hospitalRepository.findSingleton()
                .orElseThrow(() -> new NotFoundException(messageUtils.getMessage("hospital.notfound")));
    }
}
