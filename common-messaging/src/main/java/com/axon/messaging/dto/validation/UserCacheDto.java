package com.axon.messaging.dto.validation;

import lombok.*;


@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCacheDto {
    private Long userId;
    private Grade grade;
    private Integer age;
    //TODO: 추가 속성 추가 및 User_Summary 추가 고려2
}
