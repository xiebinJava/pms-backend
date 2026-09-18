package com.brad.pms.ai.context;

import java.time.Instant;
import java.util.Map;

final class ContextSnapshotFactory {

    private ContextSnapshotFactory() {
    }

    static PageContextSnapshot create(PageContextRequest request, String contextId,
                                      String version, Map<String, Object> data) {
        return new PageContextSnapshot(
                contextId,
                request.pageType(),
                request.route(),
                request.projectId(),
                request.nodeId(),
                Instant.now(),
                version,
                data);
    }
}
