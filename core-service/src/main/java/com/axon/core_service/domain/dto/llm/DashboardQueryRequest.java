package com.axon.core_service.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardQueryRequest {
    private String query;
    private String language; // e.g., "ko", "en"
}
