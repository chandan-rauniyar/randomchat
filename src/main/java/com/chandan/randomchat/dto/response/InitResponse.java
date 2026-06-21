package com.chandan.randomchat.dto.response;

import com.chandan.randomchat.model.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitResponse {

    private UUID userId;
    private String username;
    private GenderType gender;
    private String countryCode;
    private String jwt;             // Android saves this to DataStore for all future requests

    private boolean isNewUser;      // true = show onboarding screen
    private boolean isBanned;
    private String banReason;
    private Instant banExpiresAt;   // null = permanent

    private WalletResponse wallet;
}
