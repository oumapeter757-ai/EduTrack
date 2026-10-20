package com.peterscode.edutrack.security;

import com.peterscode.edutrack.school.enums.ServiceModule;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresModule {
    ServiceModule value();
}
