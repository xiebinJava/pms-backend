package com.brad.pms.dto.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class ImportPreviewDTO {
    private String jobId;
    private String importType;
    private String filename;
    private Integer rowCount;
    private List<Map<String, String>> rows = new ArrayList<>();
    private List<ImportRowErrorDTO> errors = new ArrayList<>();
}
