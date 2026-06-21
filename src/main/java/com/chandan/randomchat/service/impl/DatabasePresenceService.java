package com.chandan.randomchat.service.impl;

import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.GenderType;
import com.chandan.randomchat.repository.OnlineUserRepository;
import com.chandan.randomchat.repository.UserRepository;
import com.chandan.randomchat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "presence.backend", havingValue = "database", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DatabasePresenceService implements PresenceService {

    private final OnlineUserRepository onlineUserRepository;
    private final UserRepository       userRepository;

    @Override
    @Transactional
    public void markOnline(User user, String fcmToken) {

        UUID       userId        = user.getId();
        String     appId         = user.getAppId();
        GenderType gender        = user.getGender();
        String     countryCode   = user.getCountryCode();
        GenderType genderFilter  = user.getActiveGenderFilter();
        String     countryFilter = user.getActiveCountryFilter();
        Instant    now           = Instant.now();

        // ── ROOT CAUSE OF PREVIOUS ERRORS ────────────────────────────────────
        //
        // Error 1: "detached entity passed to persist"
        //   → User loaded in one @Transactional, passed to another = detached.
        //
        // Error 2: ObjectOptimisticLockingFailureException
        //   → @MapsId with manually-set UUID PK confuses Hibernate isNew().
        //   → JPQL UPDATE also returned 0 rows because Hibernate mapped the
        //     field name differently than expected in the WHERE clause.
        //   → Both paths (UPDATE returning 0, then save()) caused INSERT on
        //     an already-existing row.
        //
        // FINAL FIX: Use native PostgreSQL INSERT ... ON CONFLICT DO UPDATE
        //   - This is a TRUE atomic upsert at the database level
        //   - Bypasses Hibernate's isNew() check entirely
        //   - No entity state management — no detached/managed confusion
        //   - Works correctly on every subsequent heartbeat call
        //   - One SQL statement = INSERT if not exists, UPDATE if exists
        // ─────────────────────────────────────────────────────────────────────

        onlineUserRepository.upsertPresence(
                userId, appId,
                gender != null ? gender.name() : null,
                countryCode,
                genderFilter != null ? genderFilter.name() : null,
                countryFilter,
                fcmToken,
                now
        );

        // Update last_seen_at on users table — direct UPDATE, no entity load
        userRepository.updateLastSeen(userId, now);

        log.debug("Presence upserted for user {}", userId);
    }

    @Override
    @Transactional
    public void markOffline(UUID userId) {
        onlineUserRepository.deleteById(userId);
        log.debug("User {} marked offline", userId);
    }

    @Override
    public boolean isOnline(UUID userId) {
        return onlineUserRepository.existsById(userId);
    }

    @Override
    public long getOnlineCount(String appId) {
        return onlineUserRepository.countByAppId(appId);
    }

    @Override
    @Transactional
    public void cleanStaleEntries() {
        Instant threshold = Instant.now().minusSeconds(60);
        onlineUserRepository.deleteByLastHeartbeatBefore(threshold);
        log.debug("Cleaned stale presence entries older than {}", threshold);
    }
}