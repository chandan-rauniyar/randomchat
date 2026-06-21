package com.chandan.randomchat.dto.response;

import com.chandan.randomchat.model.User;
import com.chandan.randomchat.model.enums.GenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {

    private int coinBalance;
    private int matchCredits;
    private int filterCreditsGender;
    private int filterCreditsCountry;
    private GenderType activeGenderFilter;      // null = global
    private String activeCountryFilter;          // null = global

    /** Build from a User entity — used everywhere that returns wallet state. */
    public static WalletResponse from(User user) {
        return WalletResponse.builder()
                .coinBalance(user.getCoinBalance())
                .matchCredits(user.getMatchCredits())
                .filterCreditsGender(user.getFilterCreditsGender())
                .filterCreditsCountry(user.getFilterCreditsCountry())
                .activeGenderFilter(user.getActiveGenderFilter())
                .activeCountryFilter(user.getActiveCountryFilter())
                .build();
    }
}
