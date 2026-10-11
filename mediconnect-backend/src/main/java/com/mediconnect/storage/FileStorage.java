package com.mediconnect.storage;

import org.springframework.core.io.Resource;

/**
 * Where uploaded files live. Callers only ever see an opaque key, so the local-disk implementation can be swapped
 * for object storage (S3, MinIO...) without touching the services.
 */
public interface FileStorage {

    /** Stores the bytes and returns a new random key. */
    String save(byte[] content);

    /** The file for a key returned by save. */
    Resource load(String key);

    /** Best-effort removal, used only to clean up when the database insert after a save fails. */
    void delete(String key);
}
