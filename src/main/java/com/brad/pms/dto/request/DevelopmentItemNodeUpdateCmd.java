package com.brad.pms.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
public class DevelopmentItemNodeUpdateCmd {
    private Long ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Map<String, JsonNode> fieldValues;
    private Integer version;
}
