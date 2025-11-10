package com.axon.core_service.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Converter
public class TriggerConditionPayloadConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE_REF = new TypeReference<>() {};

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    /**
     * Convert a trigger condition payload map into its JSON string representation for database storage.
     *
     * @param attribute the payload map to serialize; if null or empty, it is stored as "{}"
     * @return the JSON string representation of the provided map (or "{}" for null/empty)
     * @throws IllegalArgumentException if the map cannot be serialized to JSON
     */
    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize trigger condition payload", e);
        }
    }

    /**
     * Convert the JSON string from the database into an immutable map representing the trigger condition payload.
     *
     * @param dbData the JSON string stored in the database (may be null or blank)
     * @return an unmodifiable Map preserving insertion order with the deserialized payload; returns an empty map if `dbData` is null, blank, or deserializes to an empty object
     * @throws IllegalArgumentException if the input cannot be parsed as JSON
     */
    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(dbData, TYPE_REF);
            return parsed.isEmpty()
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to deserialize trigger condition payload", e);
        }
    }
}