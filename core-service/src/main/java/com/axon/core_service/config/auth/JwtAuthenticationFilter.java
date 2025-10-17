package com.axon.core_service.config.auth;

import com.axon.core_service.util.CookieUtils;
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

import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends GenericFilterBean {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    // 쿠키 이름을 상수로 정의
    public static final String ACCESS_TOKEN_COOKIE_NAME = "accessToken";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Extracts a JWT from the incoming HTTP request (Authorization header or accessToken cookie), validates it,
     * and, if valid, stores the resulting Authentication in the SecurityContext before continuing the filter chain.
     *
     * @param request  the incoming servlet request (expected to be an HttpServletRequest)
     * @param response the servlet response
     * @param chain    the filter chain to continue processing
     * @throws IOException      if an I/O error occurs during filtering
     * @throws ServletException if the filtering fails for other servlet-related reasons
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String jwt = resolveToken(httpServletRequest);
        String requestURI = httpServletRequest.getRequestURI();

        if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
            Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.info("JWT Auth OK: Security Context에 '" + authentication.getName() + "' 인증 정보를 저장했습니다, uri: " + requestURI);
        } else {
            logger.debug("JWT Auth FAIL: 유효한 JWT 토큰이 없습니다, uri: " + requestURI);
        }

        chain.doFilter(request, response);
    }

    /**
     * Extracts a JWT from the HTTP request by checking the Authorization header first and falling back to the accessToken cookie.
     *
     * @param request the HTTP servlet request to extract the token from
     * @return the JWT string if present (from a `Bearer ` Authorization header or the `accessToken` cookie), `null` otherwise
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