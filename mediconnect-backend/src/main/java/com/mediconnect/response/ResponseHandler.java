package com.mediconnect.response;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public class ResponseHandler {

    private ResponseHandler() {}

    public static ResponseEntity<Object> generateResponse(HttpStatus httpStatus, Object data, Status status) {
        return generateResponse(httpStatus, data, status, new HttpHeaders());
    }

    /** Same envelope with extra headers (sign-in sets the refresh-token cookie this way). */
    public static ResponseEntity<Object> generateResponse(HttpStatus httpStatus, Object data, Status status,
                                                          HttpHeaders headers) {
        Map<String, Object> response = new LinkedHashMap<>();
        if (data != null) {
            response.put("data", data);
        }
        response.put("status", status);
        return new ResponseEntity<>(response, headers, httpStatus);
    }

    public static <T> ResponseEntity<Object> generatePageResponse(HttpStatus httpStatus, PageResponse<T> page, Status status) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", page.getContent());
        response.put("pagination", Map.of(
                "page", page.getPage(),
                "size", page.getSize(),
                "totalElements", page.getTotalElements(),
                "totalPages", page.getTotalPages(),
                "last", page.isLast()
        ));
        response.put("status", status);
        return new ResponseEntity<>(response, httpStatus);
    }
}
