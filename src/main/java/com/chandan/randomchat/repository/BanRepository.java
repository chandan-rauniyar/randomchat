package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.Ban;
import com.chandan.randomchat.model.enums.BanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BanRepository extends JpaRepository<Ban, UUID> {

    /**
     * Find the current active ban for a user.
     * Called by BanService when issuing a new ban (check for existing).
     */
    Optional<Ban> findFirstByUserIdAndIsLiftedFalseOrderByBannedAtDesc(UUID userId);

    /**
     * BanExpiryJob: find all temporary bans that have expired.
     * Runs every 5 minutes via @Scheduled.
     */
    List<Ban> findByBanTypeAndIsLiftedFalseAndExpiresAtBefore(
            BanType banType, Instant now);

    /** Admin: all bans for a user — for user detail page. */
    List<Ban> findByUserIdOrderByBannedAtDesc(UUID userId);
}