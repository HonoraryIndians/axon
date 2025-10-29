package com.axon.core_service.log;

import com.axon.messaging.dto.UserLoginInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserLoginLogs {

    @KafkaListener(topics = "userlogininfo", groupId = "loginlog")
    public void consume(UserLoginInfo loginInfo) {
        if (loginInfo == null) {
            log.warn("Received null UserLoginInfo message");
            return;
        }
        log.info("Consumed user login log: userId={} loggedAt={}", loginInfo.getUserId(), loginInfo.getLoggedAt());
    }
}
