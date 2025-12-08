package com.axon.messaging.topic;

public final class KafkaTopics {

    /**
     * Prevents instantiation of this utility class containing Kafka topic name constants.
     */
    private KafkaTopics() {
    }

    public static final String BEHAVIOR_EVENT = "axon.event.behavior";
    public static final String COMMERCE_EVENT = "axon.event.commerce";

    @Deprecated
    public static final String EVENT_RAW = "axon.event.raw";
    public static final String CAMPAIGN_ACTIVITY_COMMAND = "axon.campaign-activity.command";
    @Deprecated
    public static final String USER_LOGIN = "axon.user.login";
}