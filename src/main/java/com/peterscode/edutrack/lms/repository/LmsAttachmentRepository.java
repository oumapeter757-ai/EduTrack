package com.peterscode.edutrack.lms.repository;

import com.peterscode.edutrack.lms.entity.LmsAttachment;
import com.peterscode.edutrack.lms.entity.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LmsAttachmentRepository extends JpaRepository<LmsAttachment, Long> {

    List<LmsAttachment> findByOwnerTypeAndOwnerId(OwnerType ownerType, Long ownerId);

    void deleteByOwnerTypeAndOwnerId(OwnerType ownerType, Long ownerId);
}
