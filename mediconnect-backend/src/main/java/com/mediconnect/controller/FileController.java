package com.mediconnect.controller;

import com.mediconnect.service.RecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves files for signed links from GET /records/{id}/download-url. Open in SecurityConfig: the token is the proof.
 * Returns the file itself, not the JSON envelope (the only endpoint that does).
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final RecordService recordService;

    /** GET /files/{token} */
    @GetMapping("/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        return recordService.download(token);
    }
}
