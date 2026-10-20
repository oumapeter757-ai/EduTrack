package com.peterscode.edutrack.config;

import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.security.NoModuleRequired;
import com.peterscode.edutrack.security.RequiresModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ControllerAnnotationValidatorTest {

    @RestController
    static class ValidClassAnnotatedController {
        @GetMapping("/test1")
        public String endpoint1() { return "ok"; }
    }

    @RestController
    @RequiresModule(ServiceModule.STUDENT_MANAGEMENT)
    static class ValidModuleAnnotatedController {
        @GetMapping("/test2")
        public String endpoint2() { return "ok"; }
    }

    @RestController
    @NoModuleRequired
    static class ValidNoModuleAnnotatedController {
        @GetMapping("/test3")
        public String endpoint3() { return "ok"; }
    }

    @RestController
    static class InvalidUnannotatedController {
        @GetMapping("/unannotated")
        public String unannotatedEndpoint() { return "fail"; }
    }

    @Test
    @DisplayName("Validation succeeds when controller or method has RequiresModule or NoModuleRequired")
    void testValidationSucceedsWithProperAnnotations() throws Exception {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);

        ValidModuleAnnotatedController c1 = new ValidModuleAnnotatedController();
        Method m1 = ValidModuleAnnotatedController.class.getMethod("endpoint2");
        HandlerMethod hm1 = new HandlerMethod(c1, m1);

        ValidNoModuleAnnotatedController c2 = new ValidNoModuleAnnotatedController();
        Method m2 = ValidNoModuleAnnotatedController.class.getMethod("endpoint3");
        HandlerMethod hm2 = new HandlerMethod(c2, m2);

        when(mappings.getHandlerMethods()).thenReturn(Map.of(
                mock(RequestMappingInfo.class), hm1,
                mock(RequestMappingInfo.class), hm2
        ));

        ControllerAnnotationValidator validator = new ControllerAnnotationValidator(mappings);

        assertThatCode(validator::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Application fails startup when a RestController endpoint lacks both RequiresModule and NoModuleRequired")
    void testValidationFailsWhenMissingAnnotation() throws Exception {
        RequestMappingHandlerMapping mappings = mock(RequestMappingHandlerMapping.class);

        InvalidUnannotatedController invalidController = new InvalidUnannotatedController();
        Method method = InvalidUnannotatedController.class.getMethod("unannotatedEndpoint");
        HandlerMethod hm = new HandlerMethod(invalidController, method);

        when(mappings.getHandlerMethods()).thenReturn(Map.of(
                mock(RequestMappingInfo.class), hm
        ));

        ControllerAnnotationValidator validator = new ControllerAnnotationValidator(mappings);

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Controller endpoint must declare module access")
                .hasMessageContaining("InvalidUnannotatedController#unannotatedEndpoint");
    }
}
