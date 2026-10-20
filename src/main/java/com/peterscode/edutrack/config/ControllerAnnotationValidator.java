package com.peterscode.edutrack.config;

import com.peterscode.edutrack.security.*;
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
            boolean classMarked = type.isAnnotationPresent(RequiresModule.class) || type.isAnnotationPresent(NoModuleRequired.class);
            boolean methodMarked = h.getMethod().isAnnotationPresent(RequiresModule.class) || h.getMethod().isAnnotationPresent(NoModuleRequired.class);
            if (!classMarked && !methodMarked)
                throw new IllegalStateException("Controller endpoint must declare module access: " + type.getName() + "#" + h.getMethod().getName());
        }
    }
}
