package com.axon.core_service.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axon.core_service.domain.user.User;
import com.axon.core_service.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSummaryServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSummaryService userSummaryService;

    @Test
    @DisplayName("recordPurchase는 사용자에 대한 마지막 구매 시각을 갱신한다")
    void recordPurchaseUpdatesLastPurchase() {
        Instant occurredAt = Instant.now();
        User user = mock(User.class);
        when(userRepository.findById(eq(1L))).thenReturn(Optional.of(user));

        userSummaryService.recordPurchase(1L, occurredAt);

        verify(user).recordPurchase(eq(occurredAt));
    }

    @Test
    @DisplayName("recordPurchase는 사용자가 존재하지 않을 경우 예외를 던진다")
    void recordPurchaseThrowsIfUserNotFound() {
        when(userRepository.findById(eq(2L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSummaryService.recordPurchase(2L, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found: 2");
    }

    @Test
    @DisplayName("recordLogin은 사용자에 대한 마지막 로그인 시각을 갱신한다")
    void recordLoginUpdatesLastLogin() {
        Instant loggedAt = Instant.now();
        User user = mock(User.class);
        when(userRepository.findById(eq(1L))).thenReturn(Optional.of(user));

        userSummaryService.recordLogin(1L, loggedAt);

        verify(user).recordLogin(eq(loggedAt));
    }

    @Test
    @DisplayName("recordLogin은 사용자가 존재하지 않을 경우 예외를 던진다")
    void recordLoginThrowsIfUserNotFound() {
        when(userRepository.findById(eq(3L))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userSummaryService.recordLogin(3L, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found: 3");
    }
}
