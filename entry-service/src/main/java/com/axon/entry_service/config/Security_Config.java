package com.axon.entry_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security_Config {
    //postman
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/kafka/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/kafka/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
