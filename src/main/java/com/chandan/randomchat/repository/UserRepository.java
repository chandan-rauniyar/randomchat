package com.chandan.randomchat.repository;

import com.chandan.randomchat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;   // ← correct import

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByDeviceIdHashAndAppIdAndIsDeletedFalse(
            String deviceIdHash, String appId);

    boolean existsByUsernameAndAppId(String username, String appId);

    long countByAppId(String appId);

    long countByAppIdAndIsDeletedFalseAndIsBannedFalse(String appId);

    /**
     * Direct UPDATE — no entity load needed.
     * import MUST be: org.springframework.data.repository.query.Param
     * NOT:            io.lettuce.core.dynamic.annotation.Param
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeen WHERE u.id = :userId")
    void updateLastSeen(
            @Param("userId")   UUID    userId,
            @Param("lastSeen") Instant lastSeen
    );
}