package com.axon.core_service.domain.dashboard;

/**
 * Universal campaign funnel stages for CDP analytics.
 * These stages apply across all campaign activity types (FCFS, RAFFLE, COUPON).
 *
 * Stage progression represents the customer journey:
 * VISIT → ENGAGE → QUALIFY → PURCHASE
 */
public enum FunnelStep {
    /**
     * Customer viewed the campaign page.
     * Maps to: PAGE_VIEW events
     */
    VISIT,

    /**
     * Customer expressed intent to participate.
     * Maps to:
     * - FCFS: CLICK (participate button)
     * - RAFFLE: APPLY (submit entry)
     * - COUPON: CLAIM (request coupon)
     */
    ENGAGE,

    /**
     * Customer obtained qualification/benefit.
     * Maps to:
     * - FCFS: APPROVED (passed first-come-first-serve)
     * - RAFFLE: WON (won the draw)
     * - COUPON: ISSUED (coupon issued)
     */
    QUALIFY,

    /**
     * Customer completed purchase.
     * Maps to: PURCHASE events (all types)
     */
    PURCHASE
}
