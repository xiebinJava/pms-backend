package com.brad.pms.service;

import com.brad.pms.entity.OperationLogDO;
import com.brad.pms.mapper.OperationLogMapper;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {
    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(String action, String resourceType, Long resourceId, Object before, Object after) {
        OperationLogDO log = new OperationLogDO();
        log.setOperatorId(UserContext.userIdOrNull());
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setBeforeJson(safeJson(before));
        log.setAfterJson(safeJson(after));
        operationLogMapper.insert(log);
    }

    private String safeJson(Object value) {
        if (value == null) return null;
        try {
            String json = objectMapper.writeValueAsString(value);
            // Operation log callers pass sanitized DTOs. This last guard prevents
            // accidental credential fields from entering the audit table.
            return json.replaceAll("(?i)\\\"(password|token|refreshToken|accessToken|secret)\\\"\\s*:\\s*\\\"[^\\\"]*\\\"", "\\\"$1\\\":\\\"[REDACTED]\\\"");
        } catch (JsonProcessingException e) {
            return "{\"unserializable\":true}";
        }
    }
}
