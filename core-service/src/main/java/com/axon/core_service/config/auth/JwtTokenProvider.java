package com.axon.core_service.config.auth;

import com.axon.core_service.domain.user.CustomOAuth2User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60 * 1000L; // 30분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L; // 7일

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, ACCESS_TOKEN_EXPIRE_TIME);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, REFRESH_TOKEN_EXPIRE_TIME);
    }

    public String generateAccessToken(Long userId) {
        return generateToken(userId, ACCESS_TOKEN_EXPIRE_TIME);
    }

    public String generateRefreshToken(Long userId) {
        return generateToken(userId, REFRESH_TOKEN_EXPIRE_TIME);
    }

    private String generateToken(Long userId, long expireTime) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + expireTime);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("auth", "ROLE_SYSTEM")
                .claim("name", "System") // Default name for system tokens
                .issuedAt(new Date())
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    private String generateToken(Authentication authentication, long expireTime) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        String displayName = authentication.getName();
        if (authentication.getPrincipal() instanceof CustomOAuth2User customUser) {
            displayName = customUser.getDisplayName();
        }

        long now = (new Date()).getTime();
        Date validity = new Date(now + expireTime);

        return Jwts.builder()
                .subject(authentication.getName()) // This is usually userId (PK) from CustomOAuth2User
                .claim("auth", authorities)
                .claim("name", displayName) // Store real name
                .issuedAt(new Date())
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get("auth").toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        String name = claims.get("name", String.class);
        if (name == null) name = "User";

        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            userId = 0L; // Fallback or handle error
        }

        // Reconstruct CustomOAuth2User to carry displayName
        CustomOAuth2User principal = new CustomOAuth2User(
                authorities,
                Map.of("id", userId, "name", name), // Mock attributes
                "id",
                userId,
                name
        );

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}