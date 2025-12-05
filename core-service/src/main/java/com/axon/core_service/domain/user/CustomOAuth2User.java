package com.axon.core_service.domain.user;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends DefaultOAuth2User {

    private final Long userId;
    private final String displayName; // 사용자 실명

    /**
     * Create a CustomOAuth2User with authorities, attributes, a name-attribute key, and a system userId.
     *
     * Initializes the underlying OAuth2 user representation and preserves the additional system-specific user identifier.
     *
     * @param authorities      the authorities granted to the user
     * @param attributes       the attributes associated with the user
     * @param nameAttributeKey the key used to obtain the user's name from {@link #getAttributes()}
     * @param userId           the system-specific user identifier
     * @param displayName      the user's display name
     */
    public CustomOAuth2User(Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes,
                            String nameAttributeKey,
                            Long userId,
                            String displayName) {
        super(authorities, attributes, nameAttributeKey);
        this.userId = userId;
        this.displayName = displayName;
    }
}