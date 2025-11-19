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

    //public api
    public Long
    getVisitCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        //TODO: ES query 작성
        // - pageUrl wildcard /campaigns/{activityId}/*
        // - triggerType = PAGE_VIEW
        // - timestamp range
        return getEventCount(activityId, "PAGE_VIEW", start, end);
    }
    public Long getClickCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        //TODO: ES query 작성
        // - pageUrl wildcard /campaigns/{activityId}/*
        // - triggerType = CLICK
        // - timestamp range
        return getEventCount(activityId, "CLICK", start, end);
    }
    public Long getPurchaseCount(Long activityId, LocalDateTime start, LocalDateTime end) throws IOException {
        return getEventCount(activityId, "PURCHASE", start, end);
    }
    // == private helpers
    private Long getEventCount(Long activityId, String triggerType, LocalDateTime start, LocalDateTime end) throws IOException {
        //ES 쿼리 실행 로직
        SearchResponse<Void> response = elasticsearchClient.search(s -> s
                .index("behavior-events")  // Fixed: removed wildcard
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(buildPageUrlFilter(activityId))
                        .filter(buildTriggerTypeFilter(triggerType))
                        .filter(buildTimeRangeFilter(start, end))
                )),
                Void.class
        );

        // Use total hits count instead of aggregation
        if (response.hits().total() == null) {
            log.warn("No hits result for activityId={}, triggerType={}", activityId, triggerType);
            return 0L;
        }

        return response.hits().total().value();
    }

    private Query buildPageUrlFilter(Long activityId) {
        //pageUrl 필터 쿼리 작성 wildcard
        return Query.of(q -> q
                .wildcard(w -> w
                        .field("pageUrl.keyword")
                        .value("*/campaign-activity/" + activityId + "/*")
                )
        );
    }

    private Query buildTriggerTypeFilter(String triggerType) {
        //triggerType 필터 쿼리 작성 (use .keyword for exact match)
        return Query.of(q -> q
                .term(t -> t
                        .field("triggerType.keyword")  // Fixed: use keyword field
                        .value(triggerType)
                )
        );
    }

    private Query buildTimeRangeFilter(LocalDateTime start, LocalDateTime end) {
        //timestamp range 필터 쿼리 작성 (occurredAt is Unix epoch in seconds)
        long startEpoch = start.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = end.atZone(ZoneId.systemDefault()).toEpochSecond();

        return Query.of(q -> q
                .range(r -> r
                        .field("occurredAt")
                        .gte(JsonData.of(startEpoch))  // Fixed: use epoch seconds
                        .lte(JsonData.of(endEpoch))
                )
        );
    }


}
