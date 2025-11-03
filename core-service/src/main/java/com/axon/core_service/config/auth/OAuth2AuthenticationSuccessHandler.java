package com.axon.core_service.config.auth;

import com.axon.core_service.domain.user.CustomOAuth2User;
import com.axon.core_service.service.producer.CoreServiceKafkaProducer;
import com.axon.messaging.dto.UserLoginInfo;
import com.axon.messaging.topic.KafkaTopics;
import com.axon.util.CookieUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;


@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final CoreServiceKafkaProducer kafkaProducer;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }

        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        Optional<String> redirectUri = CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(Cookie::getValue);

        String targetUrl = redirectUri.orElse(getDefaultTargetUrl());

        // 내부 DB의 userId를 사용하도록 수정
        CustomOAuth2User oauthUser = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = oauthUser.getUserId();

        // 새로운 Authentication 객체 생성
        Authentication newAuth = new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, oauthUser.getAuthorities());

        String accessToken = jwtTokenProvider.generateAccessToken(newAuth);
        String refreshToken = jwtTokenProvider.generateRefreshToken(newAuth);
        int accessTokenMaxAge = 30 * 60;     // 30분
        int refreshTokenMaxAge = 7 * 24 * 60 * 60; // 7일

        CookieUtils.addCookie(response, "accessToken", accessToken, accessTokenMaxAge, false);
        //CookieUtils.addCookie(response, "refreshToken", refreshToken, refreshTokenMaxAge, true);
        log.info("Generated Access Token (first 15 chars): {}", accessToken.substring(0, 15));
        log.info("Generated Refresh Token (first 15 chars): {}", refreshToken.substring(0, 15));
        PublishUserLoginLog(userId);

        return UriComponentsBuilder.fromUriString(targetUrl)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();
    }

    private void PublishUserLoginLog(Long userId) {
        UserLoginInfo userLoginInfo = UserLoginInfo.builder()
                .userId(userId)
                .loggedAt(Instant.now())
                .build();
        try {
            kafkaProducer.send(KafkaTopics.USER_LOGIN, userLoginInfo);
            log.info("Handler send to Consumer : TOPIC {} User {}", KafkaTopics.USER_LOGIN, userLoginInfo.getUserId());
        } catch (Exception e) {
            log.error("Handler logger is occurred error || TOPIC {}", KafkaTopics.USER_LOGIN, e);
        }
    }

    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}
