package com.axon.core_service.config.auth;

import com.axon.util.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends GenericFilterBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // 쿠키 이름을 상수로 정의
    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Extracts a JWT from the incoming HTTP request, validates it, and if valid places the corresponding
     * Authentication into the SecurityContext before proceeding with the filter chain.
     *
     * @throws IOException if an I/O error occurs during request processing
     * @throws ServletException if the request cannot be handled
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String jwt = resolveToken(httpServletRequest);
        String requestURI = httpServletRequest.getRequestURI();

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT Auth OK: Security Context에 '{}' 인증 정보를 저장했습니다, uri: {}", authentication.getName(), requestURI);
            } else {
            logger.debug("JWT Auth FAIL: 유효한 JWT 토큰이 없습니다, uri: " + requestURI);
        }

        chain.doFilter(request, response);
    }

    /**
     * Resolve a JWT from the incoming HTTP request by first checking the Authorization header for a Bearer token,
     * then falling back to the "accessToken" cookie.
     *
     * @param request the HTTP request to inspect for a token
     * @return the JWT string if present in the Authorization header or the accessToken cookie, otherwise null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return CookieUtils.getCookie(request, ACCESS_TOKEN_COOKIE_NAME)
            .map(Cookie::getValue)
            .orElse(null);
    }
}