package com.axon.core_service.domain.dto;

import com.axon.core_service.domain.user.User;
import lombok.Getter;
import lombok.Setter;

@Getter
public class UserRequestDto {
    private String name;
    private String email;

    public UserRequestDto(User user) {
        this.name = user.getName();
        this.email = user.getEmail();
    }
}
