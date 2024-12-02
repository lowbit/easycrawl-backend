package com.rijads.easycrawl.service;

import com.rijads.easycrawl.model.User;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    @Value("${token.expiration.hours}")
    private long tokenExpirationInHours;
    public TokenService(final JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        String scope = String.join(" ", user.getAuthorities());
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer("self")
                        .issuedAt(now)
                        .expiresAt(now.plus(tokenExpirationInHours, ChronoUnit.HOURS))
                        .subject(user.getUsername())
                        .claim("scope", scope)
                        .build();
        return this.encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
