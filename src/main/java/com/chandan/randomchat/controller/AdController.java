package com.chandan.randomchat.controller;

import com.chandan.randomchat.dto.response.WalletActionResponse;
import com.chandan.randomchat.dto.response.WalletResponse;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.service.AdService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @PostMapping("/reward")
    public ResponseEntity<WalletActionResponse> recordReward(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody AdRewardRequest request) {

        User updated = adService.recordAdReward(
                userId, appId,
                request.getAdUnitId(),
                request.getVerificationToken()
        );

        return ResponseEntity.ok(WalletActionResponse.builder()
                .success(true)
                .wallet(WalletResponse.from(updated))
                .build());
    }

    // -------------------------------------------------------------------------
    // Request DTO (inner class — small enough to keep here)
    // -------------------------------------------------------------------------
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class AdRewardRequest {

        /** AdMob ad unit ID. Used for admin analytics per ad unit. */
        private String adUnitId;

        /**
         * Server-side verification token from AdMob.
         * Phase 1: can be null (verification added in Phase 2).
         * Phase 2: required — backend verifies with AdMob SSV API.
         */
        private String verificationToken;
    }
}
