package com.axon.core_service.service;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;
import com.axon.core_service.domain.Event;
import com.axon.core_service.domain.EventRepository;
import com.axon.core_service.service.strategy.FirstComeFirstServeStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventConsumerServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private EventRepository eventRepository;

    private EventConsumerService eventConsumerService;

    @BeforeEach
    void setUp() {
        FirstComeFirstServeStrategy strategy = new FirstComeFirstServeStrategy(redisTemplate, eventRepository);
        eventConsumerService = new EventConsumerService(List.of(strategy));
    }

    @Test
    @DisplayName("Kafka 메시지로 선착순 캠페인이 들어오면 최초 응모 사용자는 성공 처리된다")
    void consume_FirstComeFirstServe_FirstEntrySuccess() {
        int eventId = 1;
        Event event = Mockito.mock(Event.class);
        when(event.getLimitCount()).thenReturn(100);
        KafkaProducerDto message = new KafkaProducerDto(
                CampaignType.FIRST_COME_FIRST_SERVE,
                eventId,
                123,
                999,
                System.currentTimeMillis()
        );
        String redisKey = "event:" + eventId;
        String userId = String.valueOf(message.getUserId());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(redisKey, userId)).thenReturn(1L);
        when(setOperations.size(redisKey)).thenReturn(1L);

        eventConsumerService.consume(message);

        verify(eventRepository).findById(eventId);
        verify(setOperations).add(redisKey, userId);
        verify(setOperations).size(redisKey);
    }

    @Test
    @DisplayName("Kafka 메시지로 선착순 캠페인이 들어오더라도 중복 응모자는 무시된다")
    void consume_FirstComeFirstServe_DuplicateEntryIgnored() {
        int eventId = 2;
        Event event = Mockito.mock(Event.class);
        when(event.getLimitCount()).thenReturn(50);
        KafkaProducerDto message = new KafkaProducerDto(
                CampaignType.FIRST_COME_FIRST_SERVE,
                eventId,
                123,
                888,
                System.currentTimeMillis()
        );
        String redisKey = "event:" + eventId;
        String userId = String.valueOf(message.getUserId());

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.add(redisKey, userId)).thenReturn(0L);

        eventConsumerService.consume(message);

        verify(eventRepository).findById(eventId);
        verify(setOperations).add(redisKey, userId);
        verify(setOperations, never()).size(redisKey);
    }
}
