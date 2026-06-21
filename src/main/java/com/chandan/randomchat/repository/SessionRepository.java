package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * User history page: all sessions for a user (as user1 OR user2).
     * Paginated — never load all sessions at once.
     * Uses idx_sessions_user1_date and idx_sessions_user2_date.
     */
    @Query("""
        SELECT s FROM Session s
        WHERE (s.user1.id = :userId OR s.user2.id = :userId)
          AND s.appId = :appId
        ORDER BY s.startedAt DESC
        """)
    Page<Session> findByUserId(
            @Param("userId") UUID userId,
            @Param("appId") String appId,
            Pageable pageable);

    /** Admin: total sessions per app. */
    long countByAppId(String appId);

    /** Admin: sessions in last N hours — for activity monitoring. */
    @Query("""
        SELECT COUNT(s) FROM Session s
        WHERE s.appId = :appId AND s.startedAt >= :since
        """)
    long countByAppIdAndStartedAtAfter(
            @Param("appId") String appId,
            @Param("since") Instant since);
}

