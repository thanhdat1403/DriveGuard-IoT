
package com.driveguard.backend.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Vui lòng nhập username")
        String username,

        @NotBlank(message = "Vui lòng nhập password")
        String password

) {
}
