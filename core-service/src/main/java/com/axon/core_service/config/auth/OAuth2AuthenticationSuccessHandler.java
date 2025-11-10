package com.axon.core_service.config.auth;

import com.axon.core_service.domain.user.CustomOAuth2User;
import com.axon.core_service.domain.user.User;
import com.axon.core_service.repository.UserRepository;
import com.axon.core_service.service.UserSummaryService;
import com.axon.core_service.service.producer.CoreServiceKafkaProducer;
import com.axon.messaging.dto.UserLoginInfo;
import com.axon.messaging.dto.validation.UserCacheDto;
import com.axon.messaging.topic.KafkaTopics;
import com.axon.util.CookieUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;


@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
    private final UserRepository userRepository;
    private final CoreServiceKafkaProducer kafkaProducer;
    private final UserSummaryService userSummaryService;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Handle a successful OAuth2 authentication: determine the redirect URL, record the login event,
     * optionally cache the user's summary in Redis (1 day TTL), clear authentication attributes, and redirect.
     *
     * <p>If caching to Redis fails the exception is logged and does not prevent the redirect.</p>
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param authentication the authentication token containing the authenticated principal
     * @throws IOException if an I/O error occurs during redirect
     * @throws ServletException if a servlet error occurs during processing
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String targetUrl = determineTargetUrl(request, response, authentication);

        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect to " + targetUrl);
            return;
        }
        CustomOAuth2User CustomOAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        Long userId = CustomOAuth2User.getUserId();
        userSummaryService.recordLogin(userId, Instant.now());

        try {
            log.info("사용자 정보 redis 저장 단계 진입");
            User user = userRepository.findById(userId).orElse(null);
            if(user != null) {
                log.info("Redis 저장 전 사용자 찾기 : {}", user.getName());
                UserCacheDto userCacheDto = UserCacheDto.builder()
                        .userId(user.getId())
                        .age(user.getAge())
                        .grade(user.getGrade())
                        .build();
                // 현재는 기준을 하루로 잡았으나, 나중에 로그아웃시 사라지도록 하기도 고려해야 함
                log.info("redis 저장 전 || 캐쉬Dto : {}", new ObjectMapper().writeValueAsString(userCacheDto));
                redisTemplate.opsForValue().set("userCache:"+userId, userCacheDto, 1, TimeUnit.DAYS);
                log.info("ID: {} || 사용자의 정보를 redis에 적재합니다.", userId);
            }
        } catch (Exception e) {
            log.warn("로그인 검증 과정 중 Redis에 사용자 정보를 저장하는데 실패했습니다. 사용자 ID: {}", userId,e);
        }


        clearAuthenticationAttributes(request, response);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /**
     * Constructs a redirect URL containing newly generated JWT access and refresh tokens for the authenticated user.
     *
     * <p>Generates access and refresh tokens using the authenticated user's internal userId, stores the access token
     * in a non-HttpOnly cookie, publishes a user login event, and appends both tokens as query parameters to the
     * resolved target URL (either from a redirect-cookie or the default target URL).</p>
     *
     * @param request the incoming HTTP request (used to resolve optional redirect URI cookie)
     * @param response the HTTP response used to set the access token cookie
     * @param authentication the authentication whose principal is a CustomOAuth2User (its userId is used to generate tokens)
     * @return the redirect URL with `accessToken` and `refreshToken` query parameters
     */
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

    /**
     * Publish a user login event to the USER_LOGIN Kafka topic.
     *
     * Builds a UserLoginInfo payload with the provided userId and the current timestamp,
     * sends it to the Kafka topic, and logs success or failure.
     *
     * @param userId ID of the user who just logged in
     */
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

    /**
     * Clears stored authentication attributes and removes the OAuth2 authorization request cookies.
     *
     * @param request  the current HTTP request from which authentication attributes and cookies are cleared
     * @param response the current HTTP response used to remove authorization request cookies
     */
    protected void clearAuthenticationAttributes(HttpServletRequest request, HttpServletResponse response) {
        super.clearAuthenticationAttributes(request);
        httpCookieOAuth2AuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);
    }
}