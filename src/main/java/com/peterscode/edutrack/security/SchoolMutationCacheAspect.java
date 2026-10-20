package com.peterscode.edutrack.security;

import org.aspectj.lang.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SchoolMutationCacheAspect {
    private final ApplicationEventPublisher publisher;
    public SchoolMutationCacheAspect(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @AfterReturning("execution(* com.peterscode.edutrack.school.service.SchoolServiceImpl.update*(..)) || " +
            "execution(* com.peterscode.edutrack.school.service.SchoolServiceImpl.toggle*(..)) || " +
            "execution(* com.peterscode.edutrack.school.service.SchoolServiceImpl.create*(..))")
    public void evict(org.aspectj.lang.JoinPoint point, Object result) {
        Object[] args = point.getArgs();
        Long id = result instanceof com.peterscode.edutrack.school.dto.SchoolResponseDto dto
                ? dto.getId() : (args.length > 0 && args[0] instanceof Long l ? l : null);
        if (id != null) publisher.publishEvent(new SchoolEntitlementsEvicted(id));
    }
}
