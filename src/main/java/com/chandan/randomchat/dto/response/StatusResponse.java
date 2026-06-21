package com.chandan.randomchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {
    private boolean isBanned;
    private boolean canMatch;               // false if banned or no match_credits
    private String cannotMatchReason;       // "BANNED", "NO_MATCH_CREDITS", null if can match
    private WalletResponse wallet;
}
