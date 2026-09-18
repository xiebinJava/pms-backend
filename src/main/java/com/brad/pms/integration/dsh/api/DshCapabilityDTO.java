package com.brad.pms.integration.dsh.api;

import java.util.List;

/** Stable capability discovery response for the DSH PMS plugin. */
public record DshCapabilityDTO(
        String version,
        List<String> tools,
        List<String> scopes,
        List<String> pageTypes) {
}
