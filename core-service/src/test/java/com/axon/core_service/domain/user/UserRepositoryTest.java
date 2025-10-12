package com.axon.core_service.domain.user;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    public void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    public void saveAndFindUser() {
        // given
        String name = "testuser";
        String email = "test@example.com";

        userRepository.save(User.builder()
                .name(name)
                .email(email)
                .picture("test.jpg")
                .role(Role.USER)
                .provider("google")
                .providerId("12345")
                .build());

        // when
        List<User> userList = userRepository.findAll();

        // then
        User user = userList.get(0);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getRoleKey()).isEqualTo(Role.USER.getKey());
        assertThat(user.getCreatedAt()).isAfter(LocalDateTime.now().minusSeconds(2));
        assertThat(user.getUpdatedAt()).isAfter(LocalDateTime.now().minusSeconds(2));
    }
}
