package com.mediconnect.exception;

import com.mediconnect.config.MessageUtils;
import com.mediconnect.response.ResponseHandler;
import com.mediconnect.response.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into the same { data?, status } envelope the services use.
 * Deliberately no catch-all Exception handler: Spring already answers 404/405/415 correctly,
 * and a catch-all would turn those into 500s.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageUtils messageUtils;

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        return ResponseHandler.generateResponse(HttpStatus.NOT_FOUND, null,
                new Status(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
    }

    @ExceptionHandler(AlreadyExistException.class)
    public ResponseEntity<Object> handleAlreadyExists(AlreadyExistException ex) {
        return ResponseHandler.generateResponse(HttpStatus.CONFLICT, null,
                new Status(HttpStatus.CONFLICT.value(), ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Object> handleBadRequest(BadRequestException ex) {
        return ResponseHandler.generateResponse(HttpStatus.BAD_REQUEST, null,
                new Status(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Object> handleUnauthorized(UnauthorizedException ex) {
        return ResponseHandler.generateResponse(HttpStatus.UNAUTHORIZED, null,
                new Status(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Object> handleForbidden(ForbiddenException ex) {
        return ResponseHandler.generateResponse(HttpStatus.FORBIDDEN, null,
                new Status(HttpStatus.FORBIDDEN.value(), ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Object> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseHandler.generateResponse(HttpStatus.TOO_MANY_REQUESTS, null,
                new Status(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage()));
    }

    /** @Valid failures: data = { fieldName: message }. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseHandler.generateResponse(HttpStatus.BAD_REQUEST, errors,
                new Status(HttpStatus.BAD_REQUEST.value(), messageUtils.getMessage("validation.failed")));
    }

    /** Upload above spring.servlet.multipart.max-file-size. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseHandler.generateResponse(HttpStatus.CONTENT_TOO_LARGE, null,
                new Status(HttpStatus.CONTENT_TOO_LARGE.value(), messageUtils.getMessage("record.file.toolarge")));
    }

    /** A multipart request without the "file" part. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Object> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseHandler.generateResponse(HttpStatus.BAD_REQUEST, null,
                new Status(HttpStatus.BAD_REQUEST.value(),
                        messageUtils.getMessage("request.part.missing", ex.getRequestPartName())));
    }

    /** Malformed JSON, wrong types, or an unknown day-of-week value. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseHandler.generateResponse(HttpStatus.BAD_REQUEST, null,
                new Status(HttpStatus.BAD_REQUEST.value(), messageUtils.getMessage("request.unreadable")));
    }

    /**
     * Last line of defence when the database rejects a write the services did not foresee
     * (a race on a unique index, or deleting a row that something still references).
     * The transaction has already rolled back.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleConstraint(DataIntegrityViolationException ex) {
        return ResponseHandler.generateResponse(HttpStatus.CONFLICT, null,
                new Status(HttpStatus.CONFLICT.value(), messageUtils.getMessage("constraint.violation")));
    }
}
