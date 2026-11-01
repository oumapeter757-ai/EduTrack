package com.peterscode.edutrack.lms.config;

import com.peterscode.edutrack.lms.storage.FileScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;

@Configuration
public class FileScannerConfig {

    @Bean
    public FileScanner fileScanner() {
        return content -> "UNSCANNED";
    }
}
