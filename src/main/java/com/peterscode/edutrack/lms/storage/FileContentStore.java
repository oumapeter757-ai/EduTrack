package com.peterscode.edutrack.lms.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface FileContentStore {

    void write(String key, byte[] content, String contentType) throws IOException;

    Optional<InputStream> open(String key) throws IOException;

    void delete(String key) throws IOException;
}
