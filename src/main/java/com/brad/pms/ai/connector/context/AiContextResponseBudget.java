package com.brad.pms.ai.connector.context;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.integration.ai.api.AiWorkflowContextDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Focused responses must stay one coherent body: at most 512 KiB of UTF-8 data JSON,
 * rejected whole as 422 when exceeded. This is a transport budget, not a model token budget,
 * and only applies to the focused view (legacy callers keep the old envelope).
 */
@Component
public class AiContextResponseBudget {

    static final int FOCUSED_MAX_BYTES = 524288;
    private static final String ERROR_PREFIX = "CONTEXT_TOO_LARGE: ";

    private final ObjectMapper objectMapper;

    public AiContextResponseBudget(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void requireWithinFocusedLimit(AiWorkflowContextDTO context) {
        if (context == null || context.meta() == null) return;
        if (!"focused".equals(context.meta().view())) return;
        int bytes;
        try {
            byte[] payload = objectMapper.writeValueAsBytes(context);
            bytes = payload.length;
        } catch (IOException cannotHappenInMemory) {
            throw new IllegalStateException("聚焦上下文序列化失败", cannotHappenInMemory);
        }
        if (bytes > FOCUSED_MAX_BYTES) {
            throw BusinessException.error(ERROR_PREFIX + "聚焦读取结果为 " + bytes
                    + " 字节，超出 524288 上限，请指定节点或组件读取");
        }
    }
}
