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
