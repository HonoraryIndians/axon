package com.axon.core_service.domain.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DashboardQueryResponse {
    private String answer; // Natural language answer
    private Object data; // Structured data used for the answer
    private String queryIntent; // Debug info: what the system understood
}
