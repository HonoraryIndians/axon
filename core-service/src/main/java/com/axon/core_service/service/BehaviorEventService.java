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
                .index("behavior-events-*")
                .size(0)
                .query(q -> q.bool(b -> b
                        .filter(buildPageUrlFilter(activityId))
                        .filter(buildTriggerTypeFilter(triggerType))
                        .filter(buildTimeRangeFilter(start, end))
                ))
                .aggregations("total_count", a -> a
                        .valueCount(v -> v
                                .field("_id")
                        )
                ),
                Void.class
        );
        return (long) response.aggregations()
                .get("total_count")
                .valueCount()
                .value();
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
        //triggerType 필터 쿼리 작성
        return Query.of(q -> q
                .term(t -> t
                        .field("triggerType")
                        .value(triggerType)
                )
        );
    }

    private Query buildTimeRangeFilter(LocalDateTime start, LocalDateTime end) {
        //timestamp range 필터 쿼리 작성
        String startStr = start.atZone(ZoneId.systemDefault()).toInstant().toString();
        String endStr = end.atZone(ZoneId.systemDefault()).toInstant().toString();

        return Query.of(q -> q
                .range(r -> r
                        .field("occurredAt")
                        .gte(JsonData.of(startStr))
                        .lte(JsonData.of(endStr))
                )
        );
    }


}
