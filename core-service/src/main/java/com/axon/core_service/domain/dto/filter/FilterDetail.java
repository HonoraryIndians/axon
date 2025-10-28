package com.axon.core_service.domain.dto.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterDetail {
    private String type; // e.g., "AGE", "REGION"
    private List<String> values; // e.g., ["20-29", "30-39"]
}
