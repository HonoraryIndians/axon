package com.axon.core_service.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSummary {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "last_purchase_at")
    private Instant lastPurchaseAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    private UserSummary(User user) {
        this.user = Objects.requireNonNull(user, "user must not be null");
    }

    public static UserSummary initialize(User user) {
        return new UserSummary(user);
    }

    public void updateLastPurchaseAt(Instant occurredAt) {
        this.lastPurchaseAt = occurredAt;
    }

    public void updateLastLoginAt(Instant loggedInAt) {
        this.lastLoginAt = loggedInAt;
    }
}
