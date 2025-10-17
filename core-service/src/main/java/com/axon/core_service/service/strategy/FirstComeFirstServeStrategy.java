package com.axon.core_service.service.strategy;

import com.axon.messaging.CampaignType;
import com.axon.messaging.dto.KafkaProducerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.axon.core_service.domain.Event;
import com.axon.core_service.domain.EventRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeFirstServeStrategy implements CampaignStrategy {

    private final RedisTemplate<String, String> redisTemplate;
    private final EventRepository eventRepository; // EventRepository 주입

    @Override
    public void process(KafkaProducerDto eventDto) {
        // DB에서 이벤트 정보 조회
        Event event = eventRepository.findById(eventDto.getEventId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다. ID: " + eventDto.getEventId()));

        // 해당 이벤트의 제한 인원을 동적으로 가져옴
        int limit = event.getLimitCount();

        String eventKey = "event:" + eventDto.getEventId();
        String userIdStr = String.valueOf(eventDto.getUserId());

        Long addResult = redisTemplate.opsForSet().add(eventKey, userIdStr);

        if (addResult != null && addResult == 1) {
            Long currentEntries = redisTemplate.opsForSet().size(eventKey);

            if (currentEntries != null && currentEntries <= limit) {
                log.info("선착순 성공! Event: {}, User: {}, 현재 인원: {}/{}", eventDto.getEventId(), eventDto.getUserId(), currentEntries, limit);
                // TODO: DB 재고 감소 등 실제 비즈니스 로직 처리
            } else {
                log.info("선착순 마감. Event: {}, User: {}", eventDto.getEventId(), eventDto.getUserId());
                // TODO: 사용자에게 마감 알림을 보내는 로직 (선택 사항)
            }
        } else {
            log.info("중복 응모입니다. Event: {}, User: {}", eventDto.getEventId(), eventDto.getUserId());
        }
    }

    @Override
    public CampaignType getType() {
        return CampaignType.FIRST_COME_FIRST_SERVE;
    }
}
