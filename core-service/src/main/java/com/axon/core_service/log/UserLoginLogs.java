package com.axon.core_service.log;

import com.axon.messaging.dto.UserLoginInfo;
import com.axon.messaging.topic.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserLoginLogs {

    /**
     * Consumes user login messages from the configured Kafka topic and records them to the application log.
     *
     * If the provided `loginInfo` is null the message is ignored and a warning is logged; otherwise the user's
     * identifier and login timestamp are logged at info level.
     *
     * @param loginInfo the user login payload to process; may be null
     */
    @KafkaListener(topics = KafkaTopics.USER_LOGIN, groupId = "loginlog")
    public void consume(UserLoginInfo loginInfo) {
        if (loginInfo == null) {
            log.warn("Received null UserLoginInfo message");
            return;
        }
        log.info("Consumed user login log: userId={} loggedAt={}", loginInfo.getUserId(), loginInfo.getLoggedAt());
    }
}