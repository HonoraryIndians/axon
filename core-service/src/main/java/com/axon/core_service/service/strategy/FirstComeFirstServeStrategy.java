package com.axon.core_service.service.strategy;

import com.axon.core_service.domain.event.Event;
import com.axon.core_service.domain.evententry.EventEntryStatus;
import com.axon.core_service.repository.EventRepository;
import com.axon.core_service.service.EventEntryService;
import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeFirstServeStrategy implements CampaignStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final EventRepository eventRepository;
    private final EventEntryService eventEntryService;

    @Override
    public void process(KafkaProducerDto eventDto) {
        Event event = eventRepository.findById(eventDto.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다. ID: " + eventDto.getEventId()));

        int limit = Optional.ofNullable(event.getLimitCount()).orElse(Integer.MAX_VALUE);
        String eventKey = "event:" + eventDto.getEventId();
        String userKey = String.valueOf(eventDto.getUserId());

        Long addResult = redisTemplate.opsForSet().add(eventKey, userKey);
        boolean firstHit =  addResult != null && addResult == 1L;

        if (!firstHit) {
            log.info("중복 응모입니다. Event: {}, User: {}", eventDto.getEventId(), eventDto.getUserId());
            log.info("DB 상태를 DUPLICATED로 변경합니다.");
            eventEntryService.upsertEntry(event, eventDto, EventEntryStatus.DUPLICATED, true);
            return;
        }

        Long currentEntries = redisTemplate.opsForSet().size(eventKey);
        boolean withinLimit = currentEntries != null && currentEntries <= limit;

        if (withinLimit) {
            log.info("선착순 성공! Event: {}, User: {}, 현재 인원: {}/{}", eventDto.getEventId(), eventDto.getUserId(), currentEntries, limit);
            log.info("DB 상태를 APPROVED로 변경합니다.");
            eventEntryService.upsertEntry(event, eventDto, EventEntryStatus.APPROVED, true);
        } else {
            log.info("선착순 마감. Event: {}, User: {}", eventDto.getEventId(), eventDto.getUserId());
            log.info("DB 상태를 REJECTED로 변경합니다.");
            eventEntryService.upsertEntry(event, eventDto, EventEntryStatus.REJECTED, true);
        }
    }

    @Override
    public CampaignType getType() {
        return CampaignType.FIRST_COME_FIRST_SERVE;
    }
}
