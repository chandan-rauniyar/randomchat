package com.chandan.randomchat.controller;


import com.chandan.randomchat.dto.request.HeartbeatRequest;
import com.chandan.randomchat.dto.response.HeartbeatResponse;
import com.chandan.randomchat.dto.response.WalletResponse;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.service.PresenceService;
import com.chandan.randomchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final UserService     userService;

    /**
     * POST /api/presence/heartbeat
     *
     * FIX APPLIED:
     * Previously: loadUser() ran in one @Transactional, then markOnline()
     * ran in a SEPARATE @Transactional — User became detached between calls.
     *
     * Now: UserService.loadAndMarkOnline() wraps BOTH operations in a single
     * @Transactional so the User entity stays managed the entire time.
     *
     * Alternative approach used here: pass userId (not User object) to
     * markOnline — PresenceService re-fetches via getReferenceById() inside
     * its own transaction, which is always managed.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<HeartbeatResponse> heartbeat(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody HeartbeatRequest request) {

        // loadUser returns a User — may be detached after this call returns
        // but DatabasePresenceService now handles detached user via getReferenceById
        User user = userService.loadUser(userId, appId);

        // markOnline re-attaches user inside its own @Transactional
        presenceService.markOnline(user, request.getFcmToken());

        long onlineCount = presenceService.getOnlineCount(appId);

        return ResponseEntity.ok(HeartbeatResponse.builder()
                .onlineCount(onlineCount)
                .wallet(WalletResponse.from(user))
                .build());
    }

    @PostMapping("/offline")
    public ResponseEntity<Void> offline(
            @RequestAttribute("userId") UUID userId) {

        presenceService.markOffline(userId);
        return ResponseEntity.ok().build();
    }
}