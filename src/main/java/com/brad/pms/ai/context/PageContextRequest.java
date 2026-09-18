package com.brad.pms.ai.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Describes the page from which the assistant was opened.
 *
 * The page state is only a hint for locating the current view. Assemblers
 * must reload authoritative data from the backend before exposing it to the
 * model or using it for a command.
 */
public record PageContextRequest(
        PageContextType pageType,
        String route,
        Long projectId,
        Long nodeId,
        Map<String, Object> pageState) {

    public PageContextRequest {
        pageType = Objects.requireNonNull(pageType, "pageType");
        route = route == null ? "" : route;
        Map<String, Object> copy = pageState == null
                ? Map.of()
                : new LinkedHashMap<>(pageState);
        pageState = Collections.unmodifiableMap(copy);
    }
}
