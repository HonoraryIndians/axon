package com.axon.core_service.config.auth;

import com.axon.core_service.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtTokenProvider jwtTokenProvider;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
        private final HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;
        private final CustomLogoutHandler customLogoutHandler;

        /**
         * Creates a JwtAuthenticationFilter initialized with the configured
         * JwtTokenProvider.
         *
         * @return a JwtAuthenticationFilter that validates JWTs using the application's
         *         JwtTokenProvider
         */
        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter() {
                return new JwtAuthenticationFilter(jwtTokenProvider);
        }

        /**
         * Configure and build the application's HTTP security chain, including session,
         * CSRF,
         * authorization rules, OAuth2 login, exception handling, logout, and JWT filter
         * placement.
         *
         * @param jwtAuthenticationFilter the JWT authentication filter to add after
         *                                OAuth2 login processing
         * @return the configured SecurityFilterChain
         */
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
                        throws Exception {
                http
                                .httpBasic(httpBasic -> httpBasic.disable())
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(sm -> sm
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))  // Allow session for OAuth2 login to preserve original request
                                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.disable()))
                                .authorizeHttpRequests(authz -> authz
                                                .requestMatchers("/", "/css/**", "/images/**", "/js/**", "/uploads/**",
                                                                "/h2-console/**", "/favicon.ico", "/welcomepage",
                                                                "/welcomepage.html", "/test/**")
                                                .permitAll()
                                                .requestMatchers("/api/v1/**").permitAll()
                                                .requestMatchers("/fake/data/**").permitAll() // TODO: 가짜 데이터 생성은 나중에
                                                                                              // 반드시 제거할 것
                                                .requestMatchers("/actuator/**").permitAll() // Prometheus metrics endpoint
                                                // .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                                                .anyRequest().authenticated())
                                .exceptionHandling(exceptions -> exceptions

                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                                                antMatcher("/api/**"))
                                                .defaultAuthenticationEntryPointFor(
                                                                new HttpStatusEntryPoint(HttpStatus.OK), // 200 OK 반환 (리다이렉트 방지)
                                                                antMatcher("/test/**"))
                                                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/naver")))
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/mainshop")
                                                .deleteCookies("accessToken", "refreshToken")  // Delete JWT cookies
                                                .addLogoutHandler(customLogoutHandler)  // Custom cleanup (Redis, events)
                                                .permitAll())
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/oauth2/authorization/naver")
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2AuthenticationSuccessHandler)
                                                .failureHandler(new SimpleUrlAuthenticationFailureHandler("/")))
                                .addFilterAfter(jwtAuthenticationFilter, OAuth2LoginAuthenticationFilter.class);
                return http.build();
        }
}