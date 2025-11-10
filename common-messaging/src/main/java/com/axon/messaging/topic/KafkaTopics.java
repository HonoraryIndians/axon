package com.axon.messaging.topic;

public final class KafkaTopics {

    /**
     * Prevents instantiation of this utility class containing Kafka topic name constants.
     */
    private KafkaTopics() {
    }

    public static final String EVENT_RAW = "axon.event.raw";
    public static final String CAMPAIGN_ACTIVITY_COMMAND = "axon.campaign-activity.command";
    public static final String CAMPAIGN_ACTIVITY_LOG = "axon.campaign-activity.log";
    public static final String USER_LOGIN = "axon.user.login";
}