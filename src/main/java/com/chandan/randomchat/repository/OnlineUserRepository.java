package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.OnlineUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OnlineUserRepository extends JpaRepository<OnlineUser, UUID> {

    /**
     * TRUE atomic upsert using PostgreSQL native SQL.
     *
     * Why native SQL instead of JPQL?
     *   JPQL cannot express INSERT ... ON CONFLICT.
     *   JPQL UPDATE returned 0 rows due to @MapsId field name mapping issues.
     *   Native SQL bypasses ALL Hibernate entity state management entirely.
     *
     * ON CONFLICT (user_id) DO UPDATE:
     *   - If row does NOT exist → INSERT (first heartbeat)
     *   - If row DOES exist     → UPDATE (every subsequent heartbeat)
     *   One atomic SQL statement. No race condition. No duplicate key error.
     *
     * nativeQuery = true means we write real PostgreSQL SQL, not JPQL.
     * Parameters use ?1, ?2... positional syntax for native queries,
     * OR named :paramName syntax — both work with nativeQuery=true.
     *
     * CAST(:gender AS gender_type) — required because PostgreSQL needs explicit
     * cast when passing VARCHAR to a custom ENUM column. Without the cast,
     * PostgreSQL throws: "column is of type gender_type but expression is of type text"
     *
     * is_in_queue and is_in_call are NOT updated here — only heartbeat data.
     * Queue/call state is managed separately by the Phase 2 match engine.
     */
    @Modifying
    @Query(
            value = """
            INSERT INTO online_users (
                user_id,
                app_id,
                gender,
                country_code,
                active_gender_filter,
                active_country_filter,
                fcm_token,
                last_heartbeat,
                is_in_queue,
                is_in_call
            ) VALUES (
                :userId,
                :appId,
                CAST(:gender AS gender_type),
                :countryCode,
                CAST(:genderFilter AS gender_type),
                :countryFilter,
                :fcmToken,
                :now,
                false,
                false
            )
            ON CONFLICT (user_id) DO UPDATE SET
                app_id               = EXCLUDED.app_id,
                gender               = EXCLUDED.gender,
                country_code         = EXCLUDED.country_code,
                active_gender_filter = EXCLUDED.active_gender_filter,
                active_country_filter= EXCLUDED.active_country_filter,
                fcm_token            = EXCLUDED.fcm_token,
                last_heartbeat       = EXCLUDED.last_heartbeat
            """,
            nativeQuery = true
    )
    void upsertPresence(
            @Param("userId")        UUID    userId,
            @Param("appId")         String  appId,
            @Param("gender")        String  gender,        // String not GenderType for native SQL
            @Param("countryCode")   String  countryCode,
            @Param("genderFilter")  String  genderFilter,  // String not GenderType for native SQL
            @Param("countryFilter") String  countryFilter,
            @Param("fcmToken")      String  fcmToken,
            @Param("now")           Instant now
    );

    /**
     * Cleanup scheduler — remove stale rows every 60s.
     * User is considered offline if no heartbeat for 60 seconds.
     */
    void deleteByLastHeartbeatBefore(Instant threshold);

    /** Count online users for a specific app — returned in heartbeat response. */
    long countByAppId(String appId);

    /** Count users currently in queue — admin dashboard / Phase 2. */
    long countByAppIdAndIsInQueueTrue(String appId);

    /** Count users in active calls — admin dashboard. */
    long countByAppIdAndIsInCallTrue(String appId);

    /** Match engine Phase 2 — find queued users with compatible filters. */
    List<OnlineUser> findByAppIdAndIsInQueueTrue(String appId);
}