package com.chandan.randomchat.controller;

import com.chandan.randomchat.dto.request.InitRequest;
import com.chandan.randomchat.dto.request.UpdateFilterRequest;
import com.chandan.randomchat.dto.request.UpdateProfileRequest;
import com.chandan.randomchat.dto.response.InitResponse;
import com.chandan.randomchat.dto.response.WalletActionResponse;
import com.chandan.randomchat.dto.response.WalletResponse;
import com.chandan.randomchat.model.User;
import com.chandan.randomchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * POST /api/user/init
     * Called on EVERY app launch. No JWT required.
     *
     * Android sends: deviceId + countryCode
     * Backend returns: userId, username, jwt, wallet, isNewUser, isBanned
     *
     * Android saves jwt to DataStore — used on all future requests.
     * If isBanned → show ban screen (don't proceed to home).
     * If isNewUser or gender==null → show onboarding screen.
     */
    @PostMapping("/init")
    public ResponseEntity<InitResponse> init(
            @RequestHeader("X-App-ID") String appId,
            @Valid @RequestBody InitRequest request) {

        UserService.InitResult result = userService.initUser(
                request.getDeviceId(),
                appId,
                request.getCountryCode()
        );

        User user = result.user();

        InitResponse response = InitResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .gender(user.getGender())
                .countryCode(user.getCountryCode())
                .jwt(result.jwt())
                .isNewUser(result.isNewUser())
                .isBanned(user.getIsBanned())
                .banReason(user.getBanReason())
                .banExpiresAt(user.getBanExpiresAt())
                .wallet(WalletResponse.from(user))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/user/profile
     * Called after onboarding (save gender) or from settings screen.
     * Requires JWT → userId comes from request attribute set by JwtAuthFilter.
     */
    @PatchMapping("/profile")
    public ResponseEntity<WalletActionResponse> updateProfile(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody UpdateProfileRequest request) {

        User updated = userService.updateProfile(
                userId, appId, request.getGender(), request.getCountryCode());

        return ResponseEntity.ok(WalletActionResponse.builder()
                .success(true)
                .wallet(WalletResponse.from(updated))
                .build());
    }

    /**
     * PATCH /api/user/filter
     * Called when user taps gender or country filter chip on home screen.
     * Validates credits before applying. Returns 400 if insufficient credits.
     */
    @PatchMapping("/filter")
    public ResponseEntity<WalletActionResponse> updateFilter(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId,
            @Valid @RequestBody UpdateFilterRequest request) {

        User updated = userService.updateFilter(
                userId, appId,
                request.getActiveGenderFilter(),
                request.getActiveCountryFilter());

        return ResponseEntity.ok(WalletActionResponse.builder()
                .success(true)
                .wallet(WalletResponse.from(updated))
                .build());
    }

    /**
     * DELETE /api/user/account
     * Soft delete — marks user deleted, clears credits, preserves history.
     * Next app install creates new UUID.
     */
    @DeleteMapping("/account")
    public ResponseEntity<Void> deleteAccount(
            @RequestAttribute("userId") UUID userId,
            @RequestAttribute("appId") String appId) {

        userService.deleteAccount(userId, appId);
        return ResponseEntity.noContent().build();
    }
}
