package com.ghostcommunity.backend.payload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class OnboardingRequest {
    @NotNull
    private Long collegeId;

    private Long sectionId;

    @NotBlank
    private String ghostName;

    private String gender; // "MALE" or "FEMALE"
}
