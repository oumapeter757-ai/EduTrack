package com.peterscode.edutrack.shared.exception;

import com.peterscode.edutrack.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.data.repository.query.QueryCreationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import com.peterscode.edutrack.security.ModuleAccessDeniedException;
import com.peterscode.edutrack.student.exception.StudentRequestException;
import com.peterscode.edutrack.security.PlanLimitReachedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;

import java.util.stream.Collectors;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "(?:[\\w]+\\.)?((?:uk_student_school_email|uk_student_user_id|uk_student_school_admission|chk_school_max_students_positive|chk_school_max_teachers_positive))",
            Pattern.CASE_INSENSITIVE);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UserNotFound.class)
    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFound ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.warn("Duplicate resource conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedOperationException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedOperationException(UnauthorizedOperationException ex) {
        log.warn("Unauthorized operation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied: You do not have permission to perform this action"));
    }

    @ExceptionHandler(ModuleAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleModuleDenied(ModuleAccessDeniedException ex) {
        log.info("Module denial schoolId={},userId={},module={},errorCode={}",
                ex.getSchoolId(), ex.getUserId(), ex.getModule(), ex.getErrorCode());
        HttpStatus status = "SUBSCRIPTION_EXPIRED".equals(ex.getErrorCode())
                ? HttpStatus.PAYMENT_REQUIRED
                : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        if (ex instanceof DisabledException || ex instanceof LockedException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Account is deactivated", "ACCOUNT_DEACTIVATED"));
        }
        log.warn("Authentication failure: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(SchoolInactiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleSchoolInactive(SchoolInactiveException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), "SCHOOL_INACTIVE"));
    }

    @ExceptionHandler(SchoolRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleSchoolRequired(SchoolRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), "SCHOOL_REQUIRED"));
    }

    @ExceptionHandler(BootstrapNotConfiguredException.class)
    public ResponseEntity<ApiResponse<Void>> handleBootstrapNotConfigured(BootstrapNotConfiguredException ex) {
        log.error("Bootstrap lock row is missing; initial signup is not configured");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ex.getMessage(), "BOOTSTRAP_NOT_CONFIGURED"));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(ex.getMessage(), "RATE_LIMITED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(errorMessage.isBlank() ? "Validation failed" : errorMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Request body contains invalid or unknown fields"));
    }

    @ExceptionHandler({
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            NoResourceFoundException.class,
            QueryCreationException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.warn("Request rejected: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Invalid request"));
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic locking conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("The resource was modified by another request"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String constraint = findConstraint(ex);
        if ("uk_student_school_email".equalsIgnoreCase(constraint)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("email already used by another student in this school"));
        }
        if ("uk_student_user_id".equalsIgnoreCase(constraint)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("userId already linked to another student"));
        }
        if ("uk_student_school_admission".equalsIgnoreCase(constraint)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("admissionNumber already used by another student in this school"));
        }
        if ("chk_school_max_students_positive".equalsIgnoreCase(constraint)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Maximum students must be greater than 0"));
        }
        if ("chk_school_max_teachers_positive".equalsIgnoreCase(constraint)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Maximum teachers must be greater than 0"));
        }
        log.warn("Data integrity conflict: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("The request conflicts with an existing resource"));
    }

    private static String findConstraint(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            Matcher matcher = CONSTRAINT_PATTERN.matcher(current.getMessage() == null ? "" : current.getMessage());
            if (matcher.find()) return matcher.group(1);
            if (current instanceof SQLException sqlException && sqlException.getErrorCode() == 3819) {
                Matcher checkMatcher = Pattern.compile("Check constraint '([^']+)'", Pattern.CASE_INSENSITIVE)
                        .matcher(current.getMessage());
                if (checkMatcher.find()) return checkMatcher.group(1);
            }
        }
        return null;
    }

    @ExceptionHandler(InvalidSubscriptionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSubscriptionException(InvalidSubscriptionException ex) {
        log.warn("Invalid subscription terms: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(PlanLimitReachedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlanLimit(PlanLimitReachedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(StudentRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleStudentRequest(StudentRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }
}
