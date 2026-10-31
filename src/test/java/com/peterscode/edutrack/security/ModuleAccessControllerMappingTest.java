package com.peterscode.edutrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.peterscode.edutrack.attendance.controller.AttendanceController;
import com.peterscode.edutrack.school.enums.ServiceModule;
import com.peterscode.edutrack.timetable.dto.TimetableRequests.TermRequest;
import com.peterscode.edutrack.timetable.controller.StreamSubjectController;
import com.peterscode.edutrack.timetable.controller.TermController;
import com.peterscode.edutrack.timetable.controller.TimetableController;
import com.peterscode.edutrack.security.UserPrincipal;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class ModuleAccessControllerMappingTest {
    @Test
    void everyTimetableAndAttendanceHandlerHasItsRequiredModule() {
        assertHandlersRequire(TimetableController.class, ServiceModule.TIMETABLE);
        assertHandlersRequire(AttendanceController.class, ServiceModule.ATTENDANCE);
        assertHandlersRequire(StreamSubjectController.class, null);
    }

    @Test
    void termManagementRemainsUngatedAndRoleProtected() throws NoSuchMethodException {
        Method createTerm = TermController.class.getMethod("createTerm", TermRequest.class, UserPrincipal.class);

        assertThat(AnnotatedElementUtils.findMergedAnnotation(TermController.class, NoModuleRequired.class)).isNotNull();
        assertThat(AnnotatedElementUtils.findMergedAnnotation(
                createTerm, org.springframework.security.access.prepost.PreAuthorize.class)).isNotNull();
    }

    private void assertHandlersRequire(Class<?> controller, ServiceModule expected) {
        int handlerCount = 0;
        for (Method method : controller.getDeclaredMethods()) {
            if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                continue;
            }
            handlerCount++;
            RequiresModule requirement = AnnotatedElementUtils.findMergedAnnotation(method, RequiresModule.class);
            if (requirement == null) {
                requirement = AnnotatedElementUtils.findMergedAnnotation(controller, RequiresModule.class);
            }
            assertThat(requirement)
                    .as("%s#%s module annotation", controller.getSimpleName(), method.getName())
                    .isNotNull();
            ServiceModule methodExpected = expected == null
                    ? switch (method.getName()) {
                        case "assign", "assignments" -> ServiceModule.TEACHER_MANAGEMENT;
                        default -> ServiceModule.TIMETABLE;
                    }
                    : expected;
            assertThat(requirement.value())
                    .as("%s#%s module", controller.getSimpleName(), method.getName())
                    .isEqualTo(methodExpected);
        }
        assertThat(handlerCount).as("%s handler count", controller.getSimpleName()).isPositive();
    }
}
