package com.axon.core_service.config.auth;

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

    /**
     * Generate a refresh JWT for the given authentication.
     *
     * @param authentication the authenticated principal whose subject and authorities are embedded in the token
     * @return a refresh JWT string valid for seven days
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, REFRESH_TOKEN_EXPIRE_TIME);
    }

    /**
     * Create an access JWT for the specified user ID.
     *
     * @param userId the user identifier to set as the token subject
     * @return the generated JWT access token string
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, ACCESS_TOKEN_EXPIRE_TIME);
    }

    /**
     * Generate a refresh JWT for the given user ID.
     *
     * @param userId the user's identifier to set as the token subject
     * @return the signed JWT refresh token string (expires according to REFRESH_TOKEN_EXPIRE_TIME)
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, REFRESH_TOKEN_EXPIRE_TIME);
    }

    /**
     * Creates a signed JWT whose subject is the given user ID and which carries a fixed system role.
     *
     * @param userId     the user identifier to set as the token subject
     * @param expireTime the token lifetime in milliseconds from now
     * @return           a compact JWT string signed with the provider's key, containing the subject, an "auth" claim set to "ROLE_SYSTEM", an issued-at timestamp, and an expiration timestamp
     */
    private String generateToken(Long userId, long expireTime) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + expireTime);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("auth", "ROLE_SYSTEM") // 시스템 권한 추가
                .issuedAt(new Date())
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    /**
     * Creates a signed JWT for the given authenticated principal.
     *
     * The token's subject is set from the authentication's name and the "auth" claim
     * contains a comma-separated list of the principal's authorities.
     *
     * @param authentication the authenticated principal whose name and authorities populate the token
     * @param expireTime the token lifetime in milliseconds
     * @return a compact JWT string signed with HS256 containing subject, `auth`, issued-at, and expiration claims
     */
    private String generateToken(Authentication authentication, long expireTime) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity = new Date(now + expireTime);

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("auth", authorities)
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

        UserDetails principal = new User(claims.getSubject(), "", authorities);
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