package com.chandan.randomchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    @NotBlank(message = "fcmToken is required")
    private String fcmToken;
}
