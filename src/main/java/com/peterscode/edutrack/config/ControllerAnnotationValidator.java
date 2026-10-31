package com.peterscode.edutrack.config;

import com.peterscode.edutrack.security.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Component
public class ControllerAnnotationValidator implements SmartInitializingSingleton {
    private final RequestMappingHandlerMapping mappings;
    public ControllerAnnotationValidator(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) { this.mappings = mappings; }
    @Override public void afterSingletonsInstantiated() {
        for (HandlerMethod h : mappings.getHandlerMethods().values()) {
            Class<?> type = h.getBeanType();
            if (!type.isAnnotationPresent(RestController.class)) continue;
            boolean classMarked = AnnotatedElementUtils.findMergedAnnotation(type, RequiresModule.class) != null
                    || AnnotatedElementUtils.findMergedAnnotation(type, NoModuleRequired.class) != null;
            boolean methodRequiresModule = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), RequiresModule.class) != null;
            boolean methodNoModule = AnnotatedElementUtils.findMergedAnnotation(h.getMethod(), NoModuleRequired.class) != null;
            boolean methodMarked = methodRequiresModule || methodNoModule;
            if (!classMarked && !methodMarked)
                throw new IllegalStateException("Controller endpoint must declare module access: " + type.getName() + "#" + h.getMethod().getName());
            boolean ungated = methodNoModule || (!methodRequiresModule
                    && AnnotatedElementUtils.findMergedAnnotation(type, NoModuleRequired.class) != null);
            if (ungated && AnnotatedElementUtils.findMergedAnnotation(h.getMethod(),
                    org.springframework.security.access.prepost.PreAuthorize.class) == null) {
                throw new IllegalStateException("NoModuleRequired endpoint must declare PreAuthorize: "
                        + type.getName() + "#" + h.getMethod().getName());
            }
        }
    }
}
