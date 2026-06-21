package com.chandan.randomchat.service;


import com.chandan.randomchat.model.User;

public interface PresenceService {

    /** Called on heartbeat: mark user as online (insert or update). */
    void markOnline(User user, String fcmToken);

    /** Called on offline signal or app close. */
    void markOffline(java.util.UUID userId);

    /** Check if a specific user is currently online. */
    boolean isOnline(java.util.UUID userId);

    /** Count of online users for a specific app — for heartbeat response. */
    long getOnlineCount(String appId);

    /** Remove all stale presence entries. Called by scheduler every 60s. */
    void cleanStaleEntries();
}