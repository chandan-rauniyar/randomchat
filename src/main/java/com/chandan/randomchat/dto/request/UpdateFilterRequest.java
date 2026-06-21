package com.chandan.randomchat.dto.request;

import com.chandan.randomchat.model.enums.GenderType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFilterRequest {
    /**
     * NULL = global (no gender filter).
     * MALE or FEMALE = apply gender filter.
     * Backend validates filter_credits_gender > 0 before accepting non-null.
     */
    private GenderType activeGenderFilter;

    /**
     * NULL = global (no country filter).
     * "IN", "US" etc. = apply country filter.
     * Backend validates filter_credits_country > 0 before accepting non-null.
     */
    @Size(max = 5)
    private String activeCountryFilter;
}
