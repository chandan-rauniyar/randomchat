package com.chandan.randomchat.controller;

import com.chandan.randomchat.dto.request.BuyCreditsRequest;
import com.chandan.randomchat.dto.response.WalletActionResponse;
import com.chandan.randomchat.dto.response.WalletResponse;
import com.chandan.randomchat.dto.response.WalletStateResponse;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.repository.AdViewRepository;
import com.chandan.randomchat.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final AdViewRepository adViewRepository;

    /**
     * POST /api/wallet/buy-credits
     * Called when user taps "Buy 20 Matches — 10 coins" in Shop screen.
     *
     * Request: { purchaseType: "MATCH", coinCost: 10 }
     * Backend verifies coinCost against app_config (never trusts client value).
     * Returns updated wallet on success.
     * Returns 400 with error code on failure (INSUFFICIENT_COINS, PRICE_MISMATCH).
     */
    @PostMapping("/buy-credits")
    public ResponseEntity<WalletActionResponse> buyCredits(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody BuyCreditsRequest request) {

        User updated = walletService.buyCredits(
                userId, appId,
                request.getPurchaseType(),
                request.getCoinCost());

        return ResponseEntity.ok(WalletActionResponse.builder()
                .success(true)
                .wallet(WalletResponse.from(updated))
                .build());
    }

    /**
     * GET /api/wallet/state
     * Called when user opens Shop screen.
     * Returns wallet + pricing from app_config + ad config (daily cap, coins per ad).
     * Android renders Shop screen entirely from this response — no hardcoded prices.
     */
    @GetMapping("/state")
    public ResponseEntity<WalletStateResponse> getWalletState(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId) {

        int adsToday = adViewRepository
                .countByUserIdAndAppIdAndViewDateAndVerifiedTrue(userId, appId, LocalDate.now());

        WalletService.WalletStateInfo info = walletService.getWalletState(userId, appId, adsToday);
        User user = info.user();

        return ResponseEntity.ok(WalletStateResponse.builder()
                .wallet(WalletResponse.from(user))
                .pricing(info.pricing())
                .adConfig(WalletStateResponse.AdConfig.builder()
                        .coinsPerAd(info.adCoinsReward())
                        .firstDailyBonus(info.firstDailyBonus())
                        .adsWatchedToday(info.adsWatchedToday())
                        .dailyCap(info.dailyCap())
                        .canWatchMore(info.adsWatchedToday() < info.dailyCap())
                        .build())
                .build());
    }
}