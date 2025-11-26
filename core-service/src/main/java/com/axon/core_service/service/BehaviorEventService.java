package com.axon.core_service.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorEventService {

    private final ElasticsearchClient elasticsearchClient;

    // public api
    public Long getVisitCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        // TODO: ES query 작성
        // - pageUrl wildcard /campaigns/{activityId}/*
        // - triggerType = PAGE_VIEW
        // - timestamp range
        return getEventCount(activityId, "PAGE_VIEW", start, end);
    }

    public Long getEngageCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        // Maps to ENGAGE funnel step
        // Currently: CLICK (FCFS)
        // Future: APPLY (RAFFLE), CLAIM (COUPON)
        return getEventCountByTriggerTypes(activityId, getEngageTriggerTypes(), start, end);
    }

    public Long getQualifyCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        // Maps to QUALIFY funnel step
        // Currently: APPROVED (FCFS)
        // Future: WON (RAFFLE), ISSUED (COUPON)
        return getEventCountByTriggerTypes(activityId, getQualifyTriggerTypes(), start, end);
    }

    public Long getPurchaseCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        return getEventCount(activityId, "PURCHASE", start, end);
    }

    // == Trigger Type Mapping (extensible for future activity types)

    /**
     * Returns trigger types that map to ENGAGE funnel step.
     * Extend this list when adding new activity types (RAFFLE, COUPON).
     */
    private java.util.List<String> getEngageTriggerTypes() {
        return java.util.List.of(
                "CLICK"     // FCFS
                // Future: "APPLY", "CLAIM"
        );
    }

    /**
     * Returns trigger types that map to QUALIFY funnel step.
     * Extend this list when adding new activity types (RAFFLE, COUPON).
     */
    private java.util.List<String> getQualifyTriggerTypes() {
        return java.util.List.of(
                "APPROVED"  // FCFS
                // Future: "WON", "ISSUED"
        );
    }

    /**
     * Counts events matching any of the given trigger types.
     * Used for mapping multiple event types to a single funnel step.
     */
    private Long getEventCountByTriggerTypes(Long activityId, java.util.List<String> triggerTypes,
            LocalDateTime start, LocalDateTime end) throws IOException {
        if (triggerTypes.isEmpty()) {
            return 0L;
        }

        // For single trigger type, use existing optimized query
        if (triggerTypes.size() == 1) {
            return getEventCount(activityId, triggerTypes.get(0), start, end);
        }

        // For multiple trigger types, query with OR condition
        SearchResponse<Void> response = elasticsearchClient.search(s -> s
                .index("behavior-events")
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(buildPageUrlFilter(activityId))
                        .filter(buildMultiTriggerTypeFilter(triggerTypes))
                        .filter(buildTimeRangeFilter(start, end)))),
                Void.class);

        if (response.hits().total() == null) {
            log.warn("No hits result for activityId={}, triggerTypes={}", activityId, triggerTypes);
            return 0L;
        }

        return response.hits().total().value();
    }

    private Query buildMultiTriggerTypeFilter(java.util.List<String> triggerTypes) {
        return Query.of(q -> q
                .bool(b -> {
                    for (String triggerType : triggerTypes) {
                        b.should(s -> s.term(t -> t.field("triggerType.keyword").value(triggerType)));
                    }
                    b.minimumShouldMatch("1");
                    return b;
                }));
    }

    // Deprecated: kept for backward compatibility
    @Deprecated
    public Long getClickCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        return getEngageCount(activityId, start, end);
    }

    @Deprecated
    public Long getApprovedCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        return getQualifyCount(activityId, start, end);
    }

    public java.util.Map<Integer, Long> getHourlyTraffic(Long campaignId, LocalDateTime start, LocalDateTime end)
            throws IOException {
        // Hourly aggregation for the campaign (all activities)
        // Filter by campaignId (assuming pageUrl contains campaignId or we filter by
        // multiple activityIds)
        // For MVP, we will filter by time range and assume all traffic in that range is
        // relevant or filter by a broader pattern if possible.
        // Ideally, we should filter by list of activity IDs belonging to the campaign.
        // But for now, let's assume we pass a list of activity IDs or just use a broad
        // wildcard if the URL structure supports it.
        // Given the URL structure .../campaign-activity/{id}/..., we need to filter by
        // all activity IDs.
        // Let's change the signature to accept list of activity IDs.
        return java.util.Collections.emptyMap(); // Placeholder to be replaced by actual implementation in next step
    }

    public java.util.Map<Integer, Long> getHourlyTraffic(java.util.List<Long> activityIds, LocalDateTime start,
            LocalDateTime end) throws IOException {
        if (activityIds == null || activityIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
                .index("behavior-events")
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(buildMultiActivityUrlFilter(activityIds))
                        .filter(buildTimeRangeFilter(start, end))))
                .aggregations("hourly_traffic", a -> a
                        .dateHistogram(h -> h
                                .field("occurredAt") // epoch seconds
                                .fixedInterval(co.elastic.clients.elasticsearch._types.Time.of(t -> t.time("1h"))))),
                Void.class);

        java.util.Map<Integer, Long> hourlyTraffic = new java.util.HashMap<>();
        if (response.aggregations() != null) {
            co.elastic.clients.elasticsearch._types.aggregations.DateHistogramAggregate agg = response.aggregations()
                    .get("hourly_traffic").dateHistogram();
            for (co.elastic.clients.elasticsearch._types.aggregations.DateHistogramBucket bucket : agg.buckets()
                    .array()) {
                // Convert epoch milliseconds (from ES) to epoch seconds
                // Elasticsearch date_histogram returns epoch milliseconds in the key
                long epochMillis = (long) bucket.key(); // Direct numeric key instead of keyAsString()
                long epochSeconds = epochMillis / 1000; // Convert to seconds

                // Convert epoch seconds to hour of day (0-23)
                int hour = LocalDateTime
                        .ofInstant(java.time.Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault()).getHour();
                hourlyTraffic.put(hour, hourlyTraffic.getOrDefault(hour, 0L) + bucket.docCount());
            }
        }
        return hourlyTraffic;
    }

    private Query buildMultiActivityUrlFilter(java.util.List<Long> activityIds) {
        return Query.of(q -> q
                .bool(b -> {
                    for (Long id : activityIds) {
                        b.should(s -> s
                                .wildcard(w -> w.field("pageUrl.keyword").value("*/campaign-activity/" + id + "/*")));
                    }
                    return b;
                }));
    }

    // == private helpers
    private Long getEventCount(Long activityId, String triggerType, LocalDateTime start, LocalDateTime end)
            throws IOException {
        // ES 쿼리 실행 로직
        SearchResponse<Void> response = elasticsearchClient.search(s -> s
                .index("behavior-events") // Fixed: removed wildcard
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(buildPageUrlFilter(activityId))
                        .filter(buildTriggerTypeFilter(triggerType))
                        .filter(buildTimeRangeFilter(start, end)))),
                Void.class);

        // Use total hits count instead of aggregation
        if (response.hits().total() == null) {
            log.warn("No hits result for activityId={}, triggerType={}", activityId, triggerType);
            return 0L;
        }

        return response.hits().total().value();
    }

    private Query buildPageUrlFilter(Long activityId) {
        // pageUrl 필터 쿼리 작성 wildcard
        return Query.of(q -> q
                .wildcard(w -> w
                        .field("pageUrl.keyword")
                        .value("*/campaign-activity/" + activityId + "/*")));
    }

    private Query buildTriggerTypeFilter(String triggerType) {
        // triggerType 필터 쿼리 작성 (use .keyword for exact match)
        return Query.of(q -> q
                .term(t -> t
                        .field("triggerType.keyword") // Fixed: use keyword field
                        .value(triggerType)));
    }

    private Query buildTimeRangeFilter(LocalDateTime start, LocalDateTime end) {
        // timestamp range 필터 쿼리 작성 (occurredAt is Unix epoch in seconds)
        long startEpoch = start.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = end.atZone(ZoneId.systemDefault()).toEpochSecond();

        return Query.of(q -> q
                .range(r -> r
                        .field("occurredAt")
                        .gte(JsonData.of(startEpoch)) // Fixed: use epoch seconds
                        .lte(JsonData.of(endEpoch))));
    }

}
