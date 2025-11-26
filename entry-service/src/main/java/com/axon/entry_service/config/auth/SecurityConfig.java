package com.axon.entry_service.config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Create a JwtAuthenticationFilter configured with the injected
     * JwtTokenProvider.
     *
     * @return a JwtAuthenticationFilter initialized with the configured
     *         JwtTokenProvider
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    /**
     * Creates a CorsConfigurationSource that permits cross-origin requests from
     * http://localhost:8080
     * with specific methods, headers, and credentials enabled for all paths.
     *
     * @return a CorsConfigurationSource that allows origin http://localhost:8080;
     *         methods GET, POST, PUT, DELETE, OPTIONS; headers Authorization and
     *         Content-Type; credentials enabled; registered for /**.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:8080")); // core-service의 출처 허용
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Configures and builds the application's security filter chain: applies CORS,
     * disables HTTP Basic and CSRF,
     * sets stateless session management, permits behavior tracking and test endpoints,
     * requires authentication for "/api/v1/entries", and permits all other requests,
     * and registers the provided JWT authentication filter before the
     * username/password filter.
     *
     * @param http                    the HttpSecurity to configure
     * @param jwtAuthenticationFilter the JWT filter to insert into the filter chain
     * @return the configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // CORS 설정 적용
                .httpBasic(httpBasic -> httpBasic.disable())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/v1/behavior/events").permitAll() // Behavior tracking (frontend JS)
                        .requestMatchers("/api/v1/test/**").permitAll() // Test endpoints (!prod only)
                        .requestMatchers("/actuator/health").permitAll() // Health checks
                        .requestMatchers("/api/v1/entries").authenticated() // FCFS entry endpoint
                        .anyRequest().permitAll()) // Allow other requests
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}