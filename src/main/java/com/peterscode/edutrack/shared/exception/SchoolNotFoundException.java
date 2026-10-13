package com.peterscode.edutrack.shared.exception;

public class SchoolNotFoundException extends ResourceNotFoundException {

    public SchoolNotFoundException(String message) {
        super(message);
    }

    public SchoolNotFoundException(Long id) {
        super("School", "id", id);
    }

    public static SchoolNotFoundException byCode(String code) {
        return new SchoolNotFoundException(String.format("School not found with code: '%s'", code));
    }

    public static SchoolNotFoundException bySubdomain(String subdomain) {
        return new SchoolNotFoundException(String.format("School not found with subdomain: '%s'", subdomain));
    }
}
