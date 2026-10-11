package com.mediconnect.dto.record;

import java.time.Instant;

/** A short-lived link to the file. Open it directly (no Authorization header needed) before expiresAt. */
public record DownloadLinkResponse(String url, Instant expiresAt) {
}
