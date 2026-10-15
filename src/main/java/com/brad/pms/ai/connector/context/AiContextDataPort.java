package com.brad.pms.ai.connector.context;

import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;

/**
 * Application read boundary used by the AI connector instead of UI detail DTOs.
 * The userId argument is request metadata only; the implementation must resolve
 * authorization from the authenticated request context and must never trust a
 * caller-supplied identity for privilege escalation.
 */
public interface AiContextDataPort {

    AiWorkflowContextDTO read(String resourceType, Long resourceId,
                              ContextReadOptions options, Long userId);
}
