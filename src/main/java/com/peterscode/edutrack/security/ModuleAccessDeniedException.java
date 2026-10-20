package com.peterscode.edutrack.security;

public class ModuleAccessDeniedException extends RuntimeException {
    private final Long schoolId;
    private final Long userId;
    private final String errorCode;
    private final String module;
    public ModuleAccessDeniedException(Long schoolId, Long userId, String module, String errorCode, String message) {
        super(message);
        this.schoolId = schoolId; this.userId = userId; this.module = module; this.errorCode = errorCode;
    }
    public Long getSchoolId() { return schoolId; }
    public Long getUserId() { return userId; }
    public String getModule() { return module; }
    public String getErrorCode() { return errorCode; }
}
