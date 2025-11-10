package com.axon.core_service.domain.dto.campaignactivity.filter;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterDetail {
    private String type;
    private String operator;
    private List<String> values;
    private String phase;
}
