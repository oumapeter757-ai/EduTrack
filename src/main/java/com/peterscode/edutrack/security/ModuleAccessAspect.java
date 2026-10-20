package com.peterscode.edutrack.security;

import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

import lombok.extern.slf4j.Slf4j;

/**
 * Enforces {@link RequiresModule} access rules.
 *
 * <p>Fail-closed design:
 * <ul>
 *   <li>A null subscription status is treated as DENY.</li>
 *   <li>A null {@link SchoolEntitlements} result is treated as DENY.</li>
 *   <li>A null schoolId for a protected endpoint is treated as DENY.</li>
 *   <li>EXPIRED schools retain read-only access (GET/HEAD/OPTIONS) when a web request is available;
 *       on non-web threads, EXPIRED denies all access (safer default).</li>
 * </ul>
 *
 * <p>Annotation resolution uses {@link AnnotatedElementUtils#findMergedAnnotation} on both the
 * target method and the ultimate target class ({@link AopProxyUtils#ultimateTargetClass}),
 * which also picks up interface-level annotations.
 */
@Slf4j
@Aspect
@Component
// Spring Security 7.1.1 PRE_AUTHORIZE is order 200; run immediately after it.
@Order(201)
public class ModuleAccessAspect {

    private final ModuleAccessService access;
    private final SecurityUtils security;

    public ModuleAccessAspect(ModuleAccessService access, SecurityUtils security) {
        this.access = access;
        this.security = security;
    }

    @Around("@within(RequiresModule) || @annotation(RequiresModule) || @within(RequiresActiveSchool) || @annotation(RequiresActiveSchool)")
    public Object enforce(ProceedingJoinPoint p) throws Throwable {
        Method method = ((MethodSignature) p.getSignature()).getMethod();
        Class<?> targetClass = p.getTarget() == null
                ? method.getDeclaringClass()
                : AopProxyUtils.ultimateTargetClass(p.getTarget());

        RequiresModule req = AnnotatedElementUtils.findMergedAnnotation(method, RequiresModule.class);
        if (req == null) {
            req = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresModule.class);
        }

        if (security.isSuperAdmin()) {
            if (req != null) {
                Long adminId = security.getCurrentUserId();
                Long schoolId = security.getCurrentSchoolId();
                if (schoolId == null) {
                    for (Object arg : p.getArgs()) {
                        if (arg instanceof Long l && l > 0) {
                            schoolId = l;
                            break;
                        }
                    }
                }
                log.info("SUPER_ADMIN access audit: adminId={}, schoolId={}, module={}, action={}",
                        adminId, schoolId, req.value().name(), method.getName());
            }
            return p.proceed();
        }
        RequiresActiveSchool activeSchool = AnnotatedElementUtils.findMergedAnnotation(method, RequiresActiveSchool.class);
        if (activeSchool == null) {
            activeSchool = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresActiveSchool.class);
        }
        if (req == null && activeSchool == null) return p.proceed();
        if (activeSchool == null
                && AnnotatedElementUtils.findMergedAnnotation(method, NoModuleRequired.class) != null) {
            return p.proceed();
        }

        Long schoolId = security.getCurrentSchoolId();
        if (schoolId == null) {
            deny(null, req == null ? "SCHOOL" : req.value().name(), "SCHOOL_REQUIRED", "School context is required");
        }

        SchoolEntitlements e;
        try {
            e = access.entitlements(schoolId);
        } catch (ModuleAccessDeniedException madx) {
            throw madx;
        } catch (RuntimeException ex) {
            deny(schoolId, req == null ? "SCHOOL" : req.value().name(), "ACCESS_DENIED", "Subscription access is unavailable");
            return null; // unreachable
        }
        if (e == null) {
            deny(schoolId, req == null ? "SCHOOL" : req.value().name(), "ACCESS_DENIED", "Subscription access is unavailable");
        }

        String module = req == null ? "SCHOOL" : req.value().name();
        SubscriptionStatus status = e.status();

        // Manual deactivation (School.active == false) is ALWAYS a hard deny,
        // regardless of subscription status (including EXPIRED read-only).
        // Only a status-DERIVED inactive case may be exempted elsewhere.
        if (!e.active()) {
            deny(schoolId, module, "SCHOOL_INACTIVE", "School is inactive");
        }

        // Fail-closed switch: null status => deny; unknown => deny.
        boolean allow = false;
        if (status != null) {
            switch (status) {
                case ACTIVE, TRIAL -> allow = true;
                case GRACE -> {
                    writeSubscriptionHeaders(e, "GRACE");
                    allow = true;
                }
                case EXPIRED -> {
                    writeSubscriptionHeaders(e, "EXPIRED");
                    if (isReadOnlyRequest()) {
                        allow = true;
                    } else {
                        deny(schoolId, module, "SUBSCRIPTION_EXPIRED", "Subscription has expired");
                    }
                }
                case SUSPENDED -> deny(schoolId, module, "ACCESS_DENIED", "Subscription is suspended");
                case CANCELLED -> deny(schoolId, module, "ACCESS_DENIED", "Subscription is cancelled");
            }
        }
        if (!allow) {
            deny(schoolId, module, "ACCESS_DENIED", "Subscription access is denied");
        }

        // hasModule is still enforced even for EXPIRED read-only requests.
        if (req != null && !e.hasModule(req.value())) {
            deny(schoolId, module, "MODULE_NOT_IN_PLAN", "Module is not enabled");
        }

        return p.proceed();
    }

    private boolean isReadOnlyRequest() {
        HttpServletRequest req = currentRequest();
        if (req == null) return false; // non-web thread: fail closed.
        String m = req.getMethod();
        return "GET".equalsIgnoreCase(m) || "HEAD".equalsIgnoreCase(m) || "OPTIONS".equalsIgnoreCase(m);
    }

    private void writeSubscriptionHeaders(SchoolEntitlements e, String status) {
        HttpServletResponse resp = currentResponse();
        if (resp == null) return;
        resp.setHeader("X-Subscription-Status", status);
        resp.setHeader("X-Subscription-Days-Left", String.valueOf(e.daysLeft()));
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getRequest() : null;
    }

    private HttpServletResponse currentResponse() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return (attrs instanceof ServletRequestAttributes sra) ? sra.getResponse() : null;
    }

    private void deny(Long schoolId, String module, String code, String msg) {
        throw new ModuleAccessDeniedException(schoolId, security.getCurrentUserId(), module, code, msg);
    }
}
