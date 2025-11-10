package com.axon.core_service.domain.user;

import com.axon.core_service.domain.common.BaseTimeEntity;
import com.axon.messaging.dto.validation.Grade;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY, optional = false)
    private UserSummary userSummary;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    private String picture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String provider;

    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Grade grade;

    @Column
    private Integer age;

    /**
     * Constructs a User with the given identity and account attributes.
     *
     * @param name the user's display name
     * @param email the user's unique email address
     * @param picture URL or identifier for the user's avatar, or {@code null}
     * @param role the user's role
     * @param provider external authentication provider name, or {@code null}
     * @param providerId identifier from the external provider, or {@code null}
     * @param grade the user's grade; if {@code null}, defaults to {@code Grade.BRONZE}
     */
    @Builder
    public User(String name, String email, String picture, Role role, String provider, String providerId, Grade grade) {
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.userSummary = UserSummary.initialize(this);
        //TODO : 나이, 지역 입력방식 검토
        this.grade = grade == null ? Grade.BRONZE : grade;
    }

    /**
     * Update the user's name and profile picture.
     *
     * @param name the new display name
     * @param picture the new profile image URL or identifier; may be null
     * @return this User instance with updated fields
     */
    public User update(String name, String picture) {
        this.name = name;
        this.picture = picture;
        return this;
    }

    /**
     * Obtain the string key that identifies the user's role.
     *
     * @return the role key string
     */
    public String getRoleKey() {
        return this.role.getKey();
    }

    /**
     * Updates the user's last login timestamp.
     *
     * @param loginAt the instant when the user logged in
     */
    public void recordLogin(Instant loginAt) {
        this.userSummary.updateLastLoginAt(loginAt);
    }

    /**
     * Update the user's last purchase timestamp.
     *
     * @param purchaseAt the instant when the purchase occurred
     */
    public void recordPurchase(Instant purchaseAt) {
        this.userSummary.updateLastPurchaseAt(purchaseAt);
    }
}