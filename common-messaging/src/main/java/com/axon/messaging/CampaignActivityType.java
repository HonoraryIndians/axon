package com.axon.messaging;

public enum CampaignActivityType {
    FIRST_COME_FIRST_SERVE,
    COUPON,
    GIVEAWAY;

    /**
     * Determines whether this activity type is related to purchases.
     *
     * @return true if this activity type is FIRST_COME_FIRST_SERVE, false otherwise.
     */
    public boolean isPurchaseRelated() {
        return this == FIRST_COME_FIRST_SERVE;
    }
}