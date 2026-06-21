package com.chandan.randomchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyCreditsRequest {

    /**
     * What type of credit pack to purchase.
     * MATCH, GENDER_FILTER, COUNTRY_FILTER, BUNDLE_FILTER
     */
    @NotBlank(message = "purchaseType is required")
    private String purchaseType;

    /**
     * Coin cost claimed by client.
     * Backend ALWAYS re-reads price from app_config and verifies this matches.
     * If they differ → 400 PRICE_MISMATCH (prevents client sending 0).
     */
    private int coinCost;
}
