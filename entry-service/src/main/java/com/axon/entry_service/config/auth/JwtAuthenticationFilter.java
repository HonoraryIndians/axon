package com.axon.entry_service.config.auth;


import com.axon.util.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;



@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Populates the security context with an Authentication derived from a valid JWT on the incoming request.
     *
     * <p>Extracts a JWT from the Authorization header (Bearer) or the "accessToken" cookie, validates it using
     * the JwtTokenProvider, and sets the resulting Authentication into SecurityContextHolder when valid.
     * The filter chain is always continued regardless of token presence or validity.</p>
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String jwt = resolveToken(httpServletRequest);
        String requestURI = httpServletRequest.getRequestURI();

        if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.info("Security Context에 '" + authentication.getName() + "' 인증 정보를 저장했습니다, uri: " + requestURI);
        } else {
            logger.debug("유효한 JWT 토큰이 없습니다, uri: " + requestURI);
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts a JWT access token from the given HTTP request.
     *
     * <p>First checks the Authorization header for a Bearer token; if absent, falls back to a cookie
     * named "accessToken".</p>
     *
     * @param request the incoming HTTP request to read the header or cookie from
     * @return the token string if present, `null` otherwise
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