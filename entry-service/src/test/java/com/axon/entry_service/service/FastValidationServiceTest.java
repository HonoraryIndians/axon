package com.axon.entry_service.service;

import com.axon.entry_service.domain.CampaignActivityMeta;
import com.axon.entry_service.domain.CampaignActivityStatus;
import com.axon.entry_service.service.exception.FastValidationException;
import com.axon.messaging.dto.validation.Grade;
import com.axon.messaging.dto.validation.UserCacheDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FastValidationServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private FastValidationService fastValidationService;

    private com.axon.messaging.dto.validation.UserCacheDto eligibleUser;

    @BeforeEach
    void setUp() {
        eligibleUser = UserCacheDto.builder()
                .userId(1L)
                .grade(Grade.BRONZE)
                .age(25)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("모든 규칙을 통과하면, 예외를 발생시키지 않는다")
    void validate_shouldNotThrowException_whenAllRulesPass() {
        // given
        Map<String, Object> gradeRule = Map.of("type", "GRADE", "operator", "IN", "values",
                List.of("VIP", "GOLD", "BRONZE"), "phase", "FAST");
        Map<String, Object> ageRule = Map.of("type", "AGE", "operator", "GTE", "values", List.of("20"), "phase",
                "FAST");
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, 1L, 100, CampaignActivityStatus.ACTIVE, null, null,
                List.of(gradeRule, ageRule), true, false, 10L, null,
                com.axon.messaging.CampaignActivityType.FIRST_COME_FIRST_SERVE);
        when(valueOperations.get("userCache:1")).thenReturn(eligibleUser);

        // when
        Throwable throwable = catchThrowable(() -> fastValidationService.fastValidation(1L, meta));

        // then
        assertThat(throwable).isNull();
    }

    @Test
    @DisplayName("사용자 캐시가 없으면, FastValidationException을 발생시킨다")
    void validate_shouldThrowException_whenUserCacheNotFound() {
        // given
        Map<String, Object> gradeRule = Map.of("type", "GRADE", "operator", "IN", "values",
                List.of("VIP", "GOLD", "BRONZE"), "phase", "FAST");
        Map<String, Object> ageRule = Map.of("type", "AGE", "operator", "GTE", "values", List.of("20"), "phase",
                "FAST");
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, null, 100, CampaignActivityStatus.ACTIVE, null, null,
                List.of(gradeRule, ageRule), true, false, 10L, null,
                com.axon.messaging.CampaignActivityType.FIRST_COME_FIRST_SERVE);
        when(valueOperations.get("userCache:1")).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> fastValidationService.fastValidation(1L, meta))
                .isInstanceOf(FastValidationException.class)
                .hasFieldOrPropertyWithValue("type", "NULLUserData");
    }

    @Test
    @DisplayName("GRADE 규칙을 만족하지 못하면, FastValidationException을 발생시킨다")
    void validate_shouldThrowException_whenGradeRuleFails() {
        // given
        Map<String, Object> gradeRule = Map.of("type", "GRADE", "operator", "IN", "values", List.of("GOLD"), "phase",
                "FAST");
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, 1L, 100, CampaignActivityStatus.ACTIVE, null, null,
                List.of(gradeRule), true, false, 10L, null,
                com.axon.messaging.CampaignActivityType.FIRST_COME_FIRST_SERVE);
        when(valueOperations.get("userCache:1")).thenReturn(eligibleUser); // 사용자는 VIP 등급

        // when & then
        assertThatThrownBy(() -> fastValidationService.fastValidation(1L, meta))
                .isInstanceOf(FastValidationException.class)
                .hasFieldOrPropertyWithValue("type", "GRADE");
    }

    @Test
    @DisplayName("AGE 규칙을 만족하지 못하면, FastValidationException을 발생시킨다")
    void validate_shouldThrowException_whenAgeRuleFails() {
        // given
        Map<String, Object> ageRule = Map.of("type", "AGE", "operator", "LTE", "values", List.of("24"), "phase",
                "FAST");
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, 1L, 100, CampaignActivityStatus.ACTIVE, null, null,
                List.of(ageRule), true, false, 10L, null,
                com.axon.messaging.CampaignActivityType.FIRST_COME_FIRST_SERVE);
        when(valueOperations.get("userCache:1")).thenReturn(eligibleUser); // 사용자는 25세

        // when & then
        assertThatThrownBy(() -> fastValidationService.fastValidation(1L, meta))
                .isInstanceOf(FastValidationException.class)
                .hasFieldOrPropertyWithValue("type", "AGE");
    }

    @Test
    @DisplayName("phase가 fast가 아닌 규칙은 무시하고 통과시킨다")
    void validate_shouldIgnoreNonFastPhaseRules() {
        // given
        Map<String, Object> heavyRule = Map.of("type", "RECENT_PURCHASE", "phase", "HEAVY");
        CampaignActivityMeta meta = new CampaignActivityMeta(1L, 1L, 100, CampaignActivityStatus.ACTIVE, null, null,
                List.of(heavyRule), false, true, 10L, null,
                com.axon.messaging.CampaignActivityType.FIRST_COME_FIRST_SERVE);
        when(valueOperations.get("userCache:1")).thenReturn(eligibleUser);

        // when
        Throwable throwable = catchThrowable(() -> fastValidationService.fastValidation(1L, meta));

        // then
        assertThat(throwable).isNull();
    }
}