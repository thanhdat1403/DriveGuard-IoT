
package com.driveguard.backend.config;

import com.driveguard.backend.config.security.CustomAccessDeniedHandler;
import com.driveguard.backend.config.security.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth

                        // Health APIs - public
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health",
                                "/api/v1/health"
                        ).permitAll()

                        // Login APIs - public
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/v1/auth/login"
                        ).permitAll()

                        // Current user - requires JWT
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me",
                                "/api/v1/auth/me"
                        ).authenticated()


                        // Administration APIs - ADMIN only
                        .requestMatchers(
                                "/api/admin/**",
                                "/api/v1/admin/**"
                        ).hasRole("ADMIN")

                        // Sensor Reading APIs - ADMIN only
                        // Chỉ cho phép ADMIN đọc dữ liệu cảm biến.
                        // Các API này yêu cầu JWT hợp lệ có role ADMIN.
                        //
                        // GET /api/v1/sensor-readings/devices/{deviceId}/latest
                        // GET /api/v1/sensor-readings/devices/{deviceId}
                        //
                        // Chỉ cấp quyền GET, không mở quyền ghi hoặc xóa dữ liệu.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/sensor-readings/devices/*/latest",
                                "/api/v1/sensor-readings/devices/*"
                        ).hasRole("ADMIN")

                        // Deny all unspecified endpoints
                        // Từ chối mọi endpoint chưa được khai báo quyền.

                        // Alert APIs - ADMIN only
                        // Cho phép ADMIN đọc danh sách cảnh báo.
                        //
                        // GET /api/v1/alerts?deviceId=1
                        // GET /api/v1/alerts?deviceId=1&sosOnly=true
                        //
                        // Chỉ cấp quyền GET.
                        // Không cho phép tạo, sửa hoặc xóa cảnh báo
                        // thông qua endpoint này.

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/alerts",
                                "/api/v1/alerts/**"
                        ).hasRole("ADMIN")



                        // Dashboard APIs - ADMIN only
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/dashboard/summary"
                        ).hasRole("ADMIN")

                        // =====================================
                        // Device Assignment APIs - ADMIN only
                        // =====================================

                        // Tạo phân công thiết bị
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/device-assignments"
                        ).hasRole("ADMIN")

                        // Kết thúc phân công thiết bị
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/device-assignments/*/end"
                        ).hasRole("ADMIN")

                        // Xem phân công chưa kết thúc theo thiết bị
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/devices/*/open"
                        ).hasRole("ADMIN")

                        // Xem phân công chưa kết thúc theo tài xế
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/drivers/*/open"
                        ).hasRole("ADMIN")

                        // Xem lịch sử phân công theo thiết bị
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/devices/*/history"
                        ).hasRole("ADMIN")

                        // Xem lịch sử phân công theo tài xế
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/drivers/*/history"
                        ).hasRole("ADMIN")


                        // =====================================
                        // Device Assignment - Active APIs
                        // ADMIN only
                        // =====================================

                        // Xem phân công đang có hiệu lực của thiết bị
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/devices/*/active"
                        ).hasRole("ADMIN")

                        // Xem phân công đang có hiệu lực của tài xế
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/drivers/*/active"
                        ).hasRole("ADMIN")


                        // =====================================
                        // TEMPORARY DEBUG API - ADMIN ONLY
                        // Xoa sau khi kiem tra loi UTC
                        // =====================================
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/device-assignments/debug/database-time"
                        ).hasRole("ADMIN")


                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/drivers"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/drivers"
                        ).hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/drivers/*"
                        ).hasRole("ADMIN")


                        // Mặc định từ chối API chưa cấp quyền
                        .anyRequest().denyAll()


                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Cho phép các cổng frontend Vite khi phát triển
        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5173",
                        "http://localhost:5174",
                        "http://localhost:5175"
                )
        );

        // Các HTTP method được phép
        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        // Các HTTP header được phép
        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type",
                        "Accept"
                )
        );

        // JWT được gửi bằng Authorization: Bearer
        configuration.setAllowCredentials(false);

        // Cache preflight trong 1 giờ
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {

            String role = jwt.getClaimAsString("role");

            if (role == null || role.isBlank()) {
                return List.of();
            }

            return List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );
        });

        return converter;
    }
}
