package com.ghostcommunity.backend.payload;

import com.ghostcommunity.backend.model.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtResponse {
    private String token;
    private Long id;
    private String email;
    private UserStatus status;
    private String ghostName;
    private boolean isAdmin;

    public JwtResponse(String token, Long id, String email, UserStatus status, String ghostName) {
        this.token = token;
        this.id = id;
        this.email = email;
        this.status = status;
        this.ghostName = ghostName;
        this.isAdmin = false;
    }
}
