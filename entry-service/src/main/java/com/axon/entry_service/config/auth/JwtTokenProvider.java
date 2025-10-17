package com.axon.entry_service.config.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {

    private final Key key;

    // Token 만료 시간
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 30 * 60 * 1000L; // 30분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L; /**
     * Initializes the provider and builds the HMAC SHA-256 signing key from the configured JWT secret.
     *
     * @param secretKey the JWT secret from configuration used to derive the HS256 signing key
     */

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT access token for the given authentication with a 30-minute expiration.
     *
     * The token's subject is set to the authentication name and it contains an `auth` claim
     * with the principal's authorities as a comma-separated string.
     *
     * @param authentication the authenticated principal whose name and authorities are encoded into the token
     * @return the signed JWT access token
     */
    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication, ACCESS_TOKEN_EXPIRE_TIME);
    }

    /**
     * Creates a refresh JWT for the given authenticated principal.
     *
     * @param authentication the authentication containing the principal and authorities to embed in the token
     * @return a JWT refresh token string with a 7-day expiration
     */
    public String generateRefreshToken(Authentication authentication) {
        return generateToken(authentication, REFRESH_TOKEN_EXPIRE_TIME);
    }

    /**
     * Create a signed JWT for the given authentication containing the subject and authorities.
     *
     * The token will include the authentication name as the JWT subject and an "auth" claim
     * with a comma-separated list of granted authorities, and will expire after the given interval.
     *
     * @param authentication the authentication whose name becomes the token subject and whose granted authorities are stored in the "auth" claim
     * @param expireTime     token lifetime in milliseconds from the time of creation
     * @return               the compact serialized JWT containing the subject and "auth" claim
     */
    private String generateToken(Authentication authentication, long expireTime) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();
        Date validity = new Date(now + expireTime);

        return Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setIssuedAt(new Date())
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Reconstructs a Spring Security Authentication from the provided JWT access token.
     *
     * Extracts the token subject as the principal's username and the "auth" claim (a
     * comma-separated list of authority strings) as the principal's granted authorities.
     *
     * @param accessToken the JWT access token string containing subject and "auth" claim
     * @return an Authentication whose principal is a UserDetails with the token's username and authorities
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
     * Checks whether the provided JWT is valid and correctly signed with the configured key.
     *
     * @param token the JWT string to validate
     * @return `true` if the token is valid and its signature and format are acceptable, `false` otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
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
     * Extracts the JWT claims from the provided token string.
     *
     * @param accessToken the JWT compact serialized string
     * @return the parsed JWT Claims; if the token is expired, returns the claims carried by the ExpiredJwtException
     */
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}