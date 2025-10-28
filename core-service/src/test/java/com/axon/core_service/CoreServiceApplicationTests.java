package com.axon.core_service;

import com.axon.core_service.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestSecurityConfig.class)
class CoreServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
