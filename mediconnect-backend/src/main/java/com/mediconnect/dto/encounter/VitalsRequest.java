package com.mediconnect.dto.encounter;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * PUT /encounters/{id}/vitals: replaces all readings (left out = not measured). The ranges only reject obvious
 * typos (e.g. temperature 370 instead of 37.0); they are not clinical limits.
 */
public record VitalsRequest(

        @Min(value = 50, message = "bpSystolic must be between 50 and 260")
        @Max(value = 260, message = "bpSystolic must be between 50 and 260")
        Integer bpSystolic,

        @Min(value = 30, message = "bpDiastolic must be between 30 and 160")
        @Max(value = 160, message = "bpDiastolic must be between 30 and 160")
        Integer bpDiastolic,

        @Min(value = 20, message = "pulseRate must be between 20 and 250")
        @Max(value = 250, message = "pulseRate must be between 20 and 250")
        Integer pulseRate,

        @DecimalMin(value = "30.0", message = "temperatureC must be between 30.0 and 45.0")
        @DecimalMax(value = "45.0", message = "temperatureC must be between 30.0 and 45.0")
        BigDecimal temperatureC,

        @Min(value = 50, message = "spo2 must be between 50 and 100")
        @Max(value = 100, message = "spo2 must be between 50 and 100")
        Integer spo2,

        @DecimalMin(value = "0.5", message = "weightKg must be between 0.5 and 400")
        @DecimalMax(value = "400", message = "weightKg must be between 0.5 and 400")
        BigDecimal weightKg,

        @Min(value = 30, message = "heightCm must be between 30 and 250")
        @Max(value = 250, message = "heightCm must be between 30 and 250")
        Integer heightCm

) {
}
