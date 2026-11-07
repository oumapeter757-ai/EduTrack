package com.peterscode.edutrack.fees.repository;

import com.peterscode.edutrack.fees.entity.CallbackInbox;
import com.peterscode.edutrack.fees.enums.CallbackChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallbackInboxRepository extends JpaRepository<CallbackInbox, Long> {

    Optional<CallbackInbox> findBySchoolIdAndChannelAndProviderId(
            Long schoolId, CallbackChannel channel, String providerId);

    List<CallbackInbox> findBySchoolIdAndChannelAndProcessedFalse(
            Long schoolId, CallbackChannel channel);

    Page<CallbackInbox> findBySchoolIdAndProcessedFalse(Long schoolId, Pageable pageable);

    @Query("SELECT ci FROM CallbackInbox ci WHERE ci.processed = false AND ci.retryCount < 3 "
            + "ORDER BY ci.receivedAt ASC")
    List<CallbackInbox> findUnprocessedWithRetriesLeft();

    @Modifying
    @Query("DELETE FROM CallbackInbox ci WHERE ci.receivedAt < :cutoff")
    int deleteByReceivedAtBefore(@Param("cutoff") Instant cutoff);
}
