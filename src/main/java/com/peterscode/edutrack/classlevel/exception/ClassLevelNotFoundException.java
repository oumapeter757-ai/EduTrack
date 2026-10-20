package com.peterscode.edutrack.classlevel.exception;

import com.peterscode.edutrack.shared.exception.ResourceNotFoundException;

public class ClassLevelNotFoundException extends ResourceNotFoundException {

    public ClassLevelNotFoundException(String message) {
        super(message);
    }

    public ClassLevelNotFoundException(Long id) {
        super("ClassLevel", "id", id);
    }

    public static ClassLevelNotFoundException byCode(String code) {
        return new ClassLevelNotFoundException(String.format("ClassLevel not found with code: '%s'", code));
    }
}
