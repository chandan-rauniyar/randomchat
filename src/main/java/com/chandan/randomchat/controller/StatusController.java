package com.chandan.randomchat.controller;

import com.chandan.randomchat.dto.response.StatusResponse;
import com.chandan.randomchat.dto.response.WalletResponse;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class StatusController {

    private final UserService userService;

    /**
     * GET /api/user/status
     * Android calls this when user taps START button.
     * Determines if user can actually enter the queue.
     *
     * canMatch = false when:
     *   - isBanned = true         → show ban screen
     *   - matchCredits = 0        → show "Buy credits" bottom sheet
     */
    @GetMapping("/status")
    public ResponseEntity<StatusResponse> getStatus(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId) {

        User user = userService.loadUser(userId, appId);

        boolean canMatch = user.canMatch();
        String reason = null;

        if (user.getIsBanned()) {
            reason = "BANNED";
        } else if (user.getMatchCredits() <= 0) {
            reason = "NO_MATCH_CREDITS";
        }

        return ResponseEntity.ok(StatusResponse.builder()
                .isBanned(user.getIsBanned())
                .canMatch(canMatch)
                .cannotMatchReason(reason)
                .wallet(WalletResponse.from(user))
                .build());
    }
}