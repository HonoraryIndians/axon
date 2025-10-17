package com.axon.entry_service.config;

import com.axon.entry_service.config.auth.JwtAuthenticationFilter;
import com.axon.entry_service.config.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class Security_Config {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Configure the application's HTTP security and build a SecurityFilterChain.
     *
     * Disables HTTP Basic auth and CSRF, sets session management to STATELESS,
     * permits requests to "/api/v1/entries" (and currently allows all other requests),
     * and registers a JwtAuthenticationFilter before the UsernamePasswordAuthenticationFilter.
     *
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/v1/entries").permitAll()   //authenticated()
                        .anyRequest().permitAll() // 다른 모든 요청은 일단 허용
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}