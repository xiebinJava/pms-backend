package com.brad.pms.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OidcStartDTO {

    private String authorizationUrl;
    private String state;
}
