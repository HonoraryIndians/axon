package com.axon.entry_service.config.auth;

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

    // Token 만료 시간
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60 * 1000L; // 30분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L; /**
     * Create a JwtTokenProvider and initialize the HMAC-SHA signing key from the configured JWT secret.
     *
     * @param secretKey the JWT secret (from configuration) used to derive the HMAC-SHA key for signing and verifying tokens
     */

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Creates a JWT access token for the given authenticated principal.
     *
     * @param authentication the Authentication whose name and authorities will be embedded in the token
     * @return a JWT access token string that expires in 30 minutes
     */
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, ACCESS_TOKEN_EXPIRE_TIME);
    }

    /**
     * Create a refresh JWT embedding the authentication's principal name and authorities.
     *
     * @param authentication the authentication whose principal name and granted authorities will be included in the token
     * @return the signed JWT string to be used as a refresh token
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, REFRESH_TOKEN_EXPIRE_TIME);
    }

    /**
     * Create an access JWT for the given user ID.
     *
     * @param userId the user identifier to set as the token subject
     * @return the access token JWT string that expires in 30 minutes
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, ACCESS_TOKEN_EXPIRE_TIME);
    }

    /**
     * Create a JWT refresh token for the given user ID.
     *
     * @param userId the user's identifier to set as the token subject
     * @return the JWT refresh token string that expires in seven days
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, REFRESH_TOKEN_EXPIRE_TIME);
    }

    /**
     * Builds a signed JWT whose subject is the given user ID and which includes a system role claim.
     *
     * The token's expiration time is set to the current time plus {@code expireTime}, and the token
     * is signed with the provider's configured HS256 key. The token includes a claim named
     * {@code "auth"} with value {@code "ROLE_SYSTEM"}.
     *
     * @param userId     the user identifier to set as the JWT subject
     * @param expireTime the token lifetime in milliseconds from the current time
     * @return           the compact serialized JWT string
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
     * Builds a signed JWT for the given Authentication and expiry duration.
     *
     * @param authentication the authentication whose name is used as the token subject and whose authorities are stored in the `auth` claim as a comma-separated string
     * @param expireTime     token lifetime in milliseconds
     * @return               the signed JWT as a compact string
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

    /**
     * Reconstructs an Authentication object from the given JWT access token.
     *
     * Parses the token's claims, extracts the "auth" claim as a comma-separated list of authorities,
     * and returns a UsernamePasswordAuthenticationToken with a User principal built from the token subject and those authorities.
     *
     * @param accessToken the JWT access token containing the subject and an "auth" claim
     * @return an Authentication whose principal is the token subject and whose authorities come from the "auth" claim
     * @throws RuntimeException if the token does not contain an "auth" claim
     */
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

    /**
     * Validate a JWT string's signature and structure using the configured signing key.
     *
     * @param token the JWT compact-serialization string to validate
     * @return `true` if the token is valid and not expired, `false` otherwise
     */
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

    /**
     * Extracts JWT claims from the provided token.
     *
     * @param accessToken the JWT string to parse; may be expired
     * @return the token's Claims, or the claims carried by the ExpiredJwtException if the token has expired
     */
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(accessToken).getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}