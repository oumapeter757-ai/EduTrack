package com.peterscode.edutrack.lms.storage;

import java.io.InputStream;

public interface FileScanner {
    /**
     * Scan a file and return the scan status.
     * Default no-op implementation returns UNSCANNED.
     */
    String scan(InputStream content);
}
