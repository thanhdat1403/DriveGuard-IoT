
package com.driveguard.backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audience}")
    private String audience;

    public String generateToken(Long userId, String role) {

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
    }

    public Jwt verifyToken(String token) {
        return jwtDecoder.decode(token);
    }
}
