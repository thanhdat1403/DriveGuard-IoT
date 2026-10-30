
package com.driveguard.backend.config.security;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler
        implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "success", false,
                        "message", "Bạn không có quyền truy cập chức năng này"
                )
        );
    }
}
