
package com.driveguard.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import com.driveguard.backend.config.security.CustomAuthenticationEntryPoint;
import com.driveguard.backend.config.security.CustomAccessDeniedHandler;

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
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .formLogin(form -> form.disable())

                .httpBasic(basic -> basic.disable())


                .authorizeHttpRequests(auth -> auth

                        // API kiem tra Backend - cong khai
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/health"
                        ).permitAll()

                        // API dang nhap - cong khai
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login"
                        ).permitAll()

                        // API thong tin nguoi dung - can JWT
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/auth/me"
                        ).authenticated()

                        // API quan tri - chi ADMIN
                        .requestMatchers(
                                "/api/admin/**"
                        ).hasRole("ADMIN")

                        // Cac API khac chua duoc mo
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
