package com.peterscode.edutrack.student.audit;
import com.peterscode.edutrack.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
@Service @RequiredArgsConstructor
public class StudentAuditService {
    private final StudentAuditRepository repository;
    public void record(Long studentId, Long schoolId, String action, UserPrincipal actor, String oldValue, String newValue) {
        repository.save(StudentAudit.builder().studentId(studentId).schoolId(schoolId).action(action)
                .actorId(actor == null ? null : actor.getId()).oldValue(oldValue).newValue(newValue)
                .occurredAt(LocalDateTime.now()).build());
    }
}
