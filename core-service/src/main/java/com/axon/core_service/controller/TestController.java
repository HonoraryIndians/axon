package com.axon.core_service.controller;

import com.axon.core_service.config.auth.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@Profile("dev") // 'dev' 프로필에서만 활성화
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 개발용 테스트 토큰을 발급하는 API
     * @param userId 토큰에 포함시킬 사용자 ID
     * @return 생성된 Access Token
     */
    @GetMapping("/auth/token")
    public ResponseEntity<String> getTestToken(@RequestParam int userId) {
        // 1. 가짜 인증 정보 생성
        String userRole = "ROLE_USER"; // 기본 권한
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), // Principal (주체)는 보통 String 타입이므로 변환
                null, // Credentials (자격 증명)은 필요 없음
                Collections.singletonList(new SimpleGrantedAuthority(userRole))
        );

        // 2. JwtTokenProvider를 사용하여 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);

        // 3. 생성된 토큰을 응답 body에 담아 반환
        return ResponseEntity.ok(accessToken);
    }
}
