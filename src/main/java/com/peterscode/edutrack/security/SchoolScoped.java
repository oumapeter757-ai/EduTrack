package com.peterscode.edutrack.security;

/**
 * Marker interface for request DTOs that carry a school identifier.
 * <p>
 * Implementing this interface provides a compile-time guarantee that
 * {@link ModuleAccessAspect} can extract the school ID via reflection,
 * replacing the fragile runtime introspection with a type-safe contract.
 */
public interface SchoolScoped {

    /**
     * Returns the school ID associated with this request.
     *
     * @return the school ID, or {@code null} if not set
     */
    Long getSchoolId();
}
