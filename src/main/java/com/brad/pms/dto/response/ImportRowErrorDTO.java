package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowErrorDTO {
    private Integer row;
    private String field;
    private String message;
}
