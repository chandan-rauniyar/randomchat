package com.chandan.randomchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletActionResponse {
    private boolean success;
    private String error;           // null on success, e.g. "INSUFFICIENT_COINS" on fail
    private WalletResponse wallet;  // updated wallet state (even on failure — show current)
}
