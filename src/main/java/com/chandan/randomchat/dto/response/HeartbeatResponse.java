package com.chandan.randomchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatResponse {
    private long onlineCount;   // shown on home screen: "143 people online"
    private WalletResponse wallet;  // refreshed wallet on every heartbeat
}
