package com.chandan.randomchat.dto.request;

import com.chandan.randomchat.model.enums.GenderType;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private GenderType gender;

    @Size(max = 5)
    private String countryCode;
}
