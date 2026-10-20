package com.peterscode.edutrack.shared.exception;

public class BootstrapNotConfiguredException extends RuntimeException {
    public BootstrapNotConfiguredException() {
        super("Bootstrap signup is not configured");
    }
}
