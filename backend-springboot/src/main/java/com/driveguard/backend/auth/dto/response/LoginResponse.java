
package com.driveguard.backend.auth.dto.response;

public record LoginResponse(

        boolean success,
        String message,
        LoginData data

) {

    public record LoginData(

            String accessToken,
            String tokenType,
            String expiresIn,
            UserInfo user

    ) {
    }

    public record UserInfo(

            Long id,
            String username,
            String fullName,
            String email,
            String role

    ) {
    }
}
