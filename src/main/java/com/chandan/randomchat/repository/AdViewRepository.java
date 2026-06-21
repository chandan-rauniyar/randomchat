package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.AdView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.UUID;

public interface AdViewRepository extends JpaRepository<AdView, UUID> {

    /**
     * CRITICAL daily cap check — runs before every ad reward.
     * Must be fast: idx_adviews_daily_cap covers (user_id, app_id, view_date).
     */
    int countByUserIdAndAppIdAndViewDateAndVerifiedTrue(
            UUID userId, String appId, LocalDate viewDate);

    /**
     * Check if user has watched any VERIFIED ad today — for daily bonus detection.
     */
    boolean existsByUserIdAndAppIdAndViewDateAndVerifiedTrue(
            UUID userId, String appId, LocalDate viewDate);
}