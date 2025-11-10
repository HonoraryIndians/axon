package com.axon.core_service.domain.dto.campaignactivity.filter.converter;

import com.axon.core_service.domain.dto.campaignactivity.filter.FilterDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.util.List;

@Converter
public class FilterDetailConverter implements AttributeConverter<List<FilterDetail>, String> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Converts a list of FilterDetail objects into a JSON string suitable for database storage.
     *
     * @param attribute the list of FilterDetail to serialize; may be null or empty
     * @return a JSON string representing the list, or null if the input is null or empty
     * @throws IllegalArgumentException if serialization fails
     */
    @Override
    public String convertToDatabaseColumn(List<FilterDetail> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Error converting List<FilterDetail> to JSON", e);
        }
    }

    /**
     * Convert a JSON string from the database into a List of FilterDetail.
     *
     * @param dbData the JSON string retrieved from the database; may be null or empty
     * @return the parsed List&lt;FilterDetail&gt;, or null if {@code dbData} is null or empty
     * @throws IllegalArgumentException if the JSON cannot be deserialized into a list of FilterDetail
     */
    @Override
    public List<FilterDetail> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, new TypeReference<List<FilterDetail>>() {});
        } catch (IOException e) {
            throw new IllegalArgumentException("Error converting JSON to List<FilterDetail>", e);
        }
    }
}