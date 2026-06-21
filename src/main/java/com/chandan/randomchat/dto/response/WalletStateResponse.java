package com.chandan.randomchat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletStateResponse {
    private WalletResponse wallet;
    private PricingInfo pricing;
    private AdConfig adConfig;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PricingInfo {
        private PackInfo matchPack;
        private PackInfo genderFilterPack;
        private PackInfo countryFilterPack;
        private BundlePackInfo bundleFilterPack;

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class PackInfo {
            private int coinCost;
            private int creditsGained;
        }

        @Data @Builder @NoArgsConstructor @AllArgsConstructor
        public static class BundlePackInfo {
            private int coinCost;
            private int genderCredits;
            private int countryCredits;
        }
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AdConfig {
        private int coinsPerAd;
        private int firstDailyBonus;
        private int adsWatchedToday;
        private int dailyCap;
        private boolean canWatchMore;  // false if adsWatchedToday >= dailyCap
    }
}
