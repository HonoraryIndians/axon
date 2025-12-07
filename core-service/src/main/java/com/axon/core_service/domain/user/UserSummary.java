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
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private LocalDateTime lastPurchaseAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * Create a UserSummary associated with the given User.
     *
     * @param user the User to associate with this summary
     * @throws NullPointerException if {@code user} is null
     */
    private UserSummary(User user) {
        this.user = Objects.requireNonNull(user, "user must not be null");
    }

    /**
     * Create a new UserSummary associated with the given User.
     *
     * @param user the User to associate with the summary; must not be null
     * @return a newly constructed UserSummary linked to the provided user
     * @throws NullPointerException if {@code user} is null
     */
    public static UserSummary initialize(User user) {
        return new UserSummary(user);
    }

    /**
     * Set the timestamp of the user's most recent purchase.
     *
     * @param occurredAt the Instant when the purchase occurred, or {@code null} to clear the value
     */
    public void updateLastPurchaseAt(Instant occurredAt) {
        this.lastPurchaseAt = occurredAt != null
            ? LocalDateTime.ofInstant(occurredAt, ZoneId.of("Asia/Seoul"))
            : null;
    }

    /**
     * Update the stored timestamp of the user's last login.
     *
     * @param loggedInAt the timestamp of the login event; may be `null` to clear the stored last-login time
     */
    public void updateLastLoginAt(Instant loggedInAt) {
        this.lastLoginAt = loggedInAt != null
            ? LocalDateTime.ofInstant(loggedInAt, ZoneId.of("Asia/Seoul"))
            : null;
    }
}