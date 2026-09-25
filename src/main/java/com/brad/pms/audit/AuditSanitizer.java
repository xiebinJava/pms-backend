package com.brad.pms.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Converts audit summaries to bounded JSON without persisting credentials. */
@Component
public class AuditSanitizer {
    private static final int MAX_JSON_LENGTH = 4000;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "token", "refreshtoken", "accesstoken", "secret", "authorization", "tokenhash", "jwt");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public String sanitize(Object value) {
        if (value == null) return null;
        try {
            JsonNode sanitized = redact(objectMapper.valueToTree(value));
            String json = objectMapper.writeValueAsString(sanitized);
            return json.length() <= MAX_JSON_LENGTH ? json : "{\"truncated\":true}";
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
}
