package com.ghostcommunity.backend.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
