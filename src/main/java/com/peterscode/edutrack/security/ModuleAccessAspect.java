package com.peterscode.edutrack.security;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.school.enums.SubscriptionStatus;
import com.peterscode.edutrack.user.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@Order(201)
public class ModuleAccessAspect {
    private final ModuleAccessService access;
    private final SecurityUtils security;

    public ModuleAccessAspect(ModuleAccessService access, SecurityUtils security) {
        this.access = access;
        this.security = security;
    }

    @Around("@within(RequiresModule) || @annotation(RequiresModule) || @within(RequiresActiveSchool) || @annotation(RequiresActiveSchool)")
    public Object enforce(ProceedingJoinPoint invocation) throws Throwable {
        Method method = ((MethodSignature) invocation.getSignature()).getMethod();
        Class<?> targetClass = invocation.getTarget() == null
                ? method.getDeclaringClass()
                : AopProxyUtils.ultimateTargetClass(invocation.getTarget());
        RequiresModule requirement = AnnotatedElementUtils.findMergedAnnotation(method, RequiresModule.class);
        if (requirement == null) {
            requirement = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresModule.class);
        }
        RequiresActiveSchool activeRequirement = AnnotatedElementUtils.findMergedAnnotation(method, RequiresActiveSchool.class);
        if (activeRequirement == null) {
            activeRequirement = AnnotatedElementUtils.findMergedAnnotation(targetClass, RequiresActiveSchool.class);
        }
        if (AnnotatedElementUtils.findMergedAnnotation(method, NoModuleRequired.class) != null) {
            return invocation.proceed();
        }
        if (requirement == null && activeRequirement == null) {
            return invocation.proceed();
        }

        UserPrincipal principal = security.getCurrentPrincipal();
        if (principal == null || principal.getRole() == null) {
            deny(null, requirement == null ? null : requirement.value(), "ACCESS_DENIED", "Access denied");
        }

        Long requestedSchoolId = explicitSchoolId(method, invocation.getArgs());
        Long schoolId;
        if (principal.getRole() == Role.SUPER_ADMIN) {
            schoolId = requestedSchoolId;
            log.info("SUPER_ADMIN module access: adminId={}, schoolId={}, module={}",
                    principal.getId(), schoolId, requirement == null ? "SCHOOL" : requirement.value().name());
            if (schoolId == null) {
                deny(null, requirement == null ? null : requirement.value(),
                        "ACCESS_DENIED", "School context is required");
            }
        } else {
            schoolId = principal.getSchoolId();
            if (schoolId == null) {
                deny(null, requirement == null ? null : requirement.value(),
                        "ACCESS_DENIED", "School context is required");
            }
            if (requestedSchoolId != null && !requestedSchoolId.equals(schoolId)) {
                deny(schoolId, requirement == null ? null : requirement.value(),
                        "ACCESS_DENIED", "Access denied");
            }
        }

        SchoolEntitlements entitlements;
        try {
            entitlements = access.entitlements(schoolId);
        } catch (ModuleAccessDeniedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("School entitlement lookup failed for schoolId={}, module={}",
                    schoolId, requirement == null ? "SCHOOL" : requirement.value().name());
            deny(schoolId, requirement == null ? null : requirement.value(),
                    "ACCESS_DENIED", "Subscription access is unavailable");
            return null;
        }
        if (entitlements == null) {
            deny(schoolId, requirement == null ? null : requirement.value(),
                    "ACCESS_DENIED", "Subscription access is unavailable");
        }
        if (!entitlements.active()) {
            deny(schoolId, requirement == null ? null : requirement.value(),
                    "SCHOOL_INACTIVE", "School is inactive");
        }

        if (principal.getRole() == Role.SUPER_ADMIN) {
            return invocation.proceed();
        }

        SubscriptionStatus status = entitlements.status();
        if (status == SubscriptionStatus.EXPIRED) {
            if (!isReadOnlyRequest()) {
                deny(schoolId, requirement == null ? null : requirement.value(),
                        "SUBSCRIPTION_EXPIRED", "Subscription has expired");
            }
        } else if (status != SubscriptionStatus.ACTIVE
                && status != SubscriptionStatus.TRIAL && status != SubscriptionStatus.GRACE) {
            deny(schoolId, requirement == null ? null : requirement.value(),
                    "ACCESS_DENIED", "Subscription access is denied");
        }

        ServiceModule module = requirement == null ? null : requirement.value();
        if (module != null && !entitlements.hasModule(module)) {
            deny(schoolId, module, "MODULE_NOT_IN_PLAN", "Module is not enabled");
        }
        return invocation.proceed();
    }

    private Long explicitSchoolId(Method method, Object[] arguments) {
        List<Long> candidates = new ArrayList<>();
        HttpServletRequest request = currentRequest();
        if (request != null) {
            String queryValue = request.getParameter("schoolId");
            if (queryValue != null) {
                candidates.add(parseSchoolId(queryValue));
            }
        }
        Parameter[] parameters = method.getParameters();
        for (int index = 0; index < Math.min(parameters.length, arguments.length); index++) {
            Object argument = arguments[index];
            Parameter parameter = parameters[index];
            if (argument == null) {
                continue;
            }
            if (argument instanceof UserPrincipal) {
                continue;
            }
            String named = requestParamName(parameter);
            if (argument instanceof Long value && ("schoolId".equals(named) || "schoolId".equals(parameter.getName()))) {
                candidates.add(value);
            } else {
                Long bodySchoolId = bodySchoolId(argument);
                if (bodySchoolId != null) {
                    candidates.add(bodySchoolId);
                }
            }
        }
        if (candidates.stream().distinct().count() > 1) {
            deny(null, null, "ACCESS_DENIED", "School context is invalid");
        }
        return candidates.isEmpty() ? null : candidates.getFirst();
    }

    private String requestParamName(Parameter parameter) {
        RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
        if (requestParam == null) {
            return null;
        }
        if (!requestParam.name().isBlank()) {
            return requestParam.name();
        }
        if (!requestParam.value().isBlank()) {
            return requestParam.value();
        }
        return parameter.getName();
    }

    private Long bodySchoolId(Object argument) {
        for (String accessorName : List.of("schoolId", "getSchoolId")) {
            try {
                Method accessor = argument.getClass().getMethod(accessorName);
                Object value = accessor.invoke(argument);
                if (value == null) {
                    return null;
                }
                if (value instanceof Long schoolId) {
                    return schoolId;
                }
                deny(null, null, "ACCESS_DENIED", "School context is invalid");
            } catch (NoSuchMethodException ignored) {
                // This argument does not carry a school identifier.
            } catch (ReflectiveOperationException exception) {
                deny(null, null, "ACCESS_DENIED", "School context is unavailable");
            }
        }
        return null;
    }

    private Long parseSchoolId(String value) {
        try {
            long schoolId = Long.parseLong(value);
            if (schoolId < 1) {
                throw new NumberFormatException();
            }
            return schoolId;
        } catch (NumberFormatException exception) {
            deny(null, null, "ACCESS_DENIED", "School context is invalid");
            return null;
        }
    }

    private boolean isReadOnlyRequest() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return false;
        }
        String method = request.getMethod();
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest() : null;
    }

    private void deny(Long schoolId, ServiceModule module, String code, String message) {
        throw new ModuleAccessDeniedException(schoolId, security.getCurrentUserId(),
                module == null ? "SCHOOL" : module.name(), code, message);
    }
}
