package com.brad.pms.service;

import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "refreshtoken", "accesstoken", "secret", "authorization", "tokenhash", "jwt");
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void record(String action, String resourceType, Long resourceId, Object before, Object after) {
        long started = System.nanoTime();
        OperationLogDO log = new OperationLogDO();
        log.setOperatorId(UserContext.userIdOrNull());
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setRequestId(MDC.get("requestId"));
        log.setBeforeJson(safeJson(before));
        log.setAfterJson(safeJson(after));
        operationLogMapper.insert(log);
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        String previousAction = MDC.get("action");
        String previousResource = MDC.get("resource");
        String previousUser = MDC.get("userId");
        String previousDuration = MDC.get("durationMs");
        try {
            MDC.put("action", action == null ? "" : action);
            MDC.put("resource", resourceType == null ? "" : resourceType);
            if (log.getOperatorId() == null) MDC.remove("userId"); else MDC.put("userId", String.valueOf(log.getOperatorId()));
            MDC.put("durationMs", String.valueOf(durationMs));
            OperationLogService.log.info("audit_event");
        } finally {
            restoreMdc("action", previousAction);
            restoreMdc("resource", previousResource);
            restoreMdc("userId", previousUser);
            restoreMdc("durationMs", previousDuration);
        }
    }

    private String safeJson(Object value) {
        if (value == null) return null;
        try {
            JsonNode sanitized = redact(objectMapper.valueToTree(value));
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return "{\"unserializable\":true}";
        }
    }

    private JsonNode redact(JsonNode node) {
        if (node == null) return null;
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE_FIELDS.contains(normalize(field.getKey()))) {
                    field.setValue(object.textNode("[REDACTED]"));
                } else {
                    redact(field.getValue());
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode child : array) redact(child);
        }
        return node;
    }

    private String normalize(String field) {
        return field.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private void restoreMdc(String key, String value) {
        if (value == null) MDC.remove(key); else MDC.put(key, value);
    }
}
