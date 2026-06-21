package com.chandan.randomchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitRequest {

    /**
     * Raw Android device ID (ANDROID_ID).
     * Backend hashes this with SHA-256 — never stored raw.
     */
    @NotBlank(message = "deviceId is required")
    @Size(min = 8, max = 64, message = "deviceId must be 8-64 characters")
    private String deviceId;

    /**
     * ISO 3166-1 alpha-2 country code detected on Android.
     * Examples: "IN", "US", "GB". Optional — defaults to "XX" if null.
     */
    @Size(max = 5, message = "countryCode must be 2-5 characters")
    private String countryCode;
}
