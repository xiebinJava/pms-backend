package com.brad.pms.service;

import com.brad.pms.entity.OrgUnitDO;
import com.brad.pms.entity.OrgUnitHistoryDO;
import com.brad.pms.mapper.OrgUnitHistoryMapper;
import com.brad.pms.security.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Durable, append-only history for organization structure changes. */
@Service
@RequiredArgsConstructor
public class OrgUnitHistoryService {
    private final OrgUnitHistoryMapper historyMapper;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public void record(String action, OrgUnitDO before, OrgUnitDO after) {
        OrgUnitDO current = after != null ? after : before;
        if (current == null || current.getId() == null) return;
        OrgUnitHistoryDO history = new OrgUnitHistoryDO();
        history.setOrgUnitId(current.getId());
        history.setAction(action);
        history.setOperatorId(UserContext.userIdOrNull());
        history.setRequestId(MDC.get("requestId"));
        history.setBeforeJson(snapshot(before));
        history.setAfterJson(snapshot(after));
        historyMapper.insert(history);
    }

    public List<OrgUnitHistoryDO> list(Long orgUnitId) {
        return historyMapper.findByOrgUnitId(orgUnitId);
    }

    private String snapshot(OrgUnitDO unit) {
        if (unit == null) return null;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", unit.getId());
        values.put("parentId", unit.getParentId());
        values.put("typeId", unit.getTypeId());
        values.put("code", unit.getCode());
        values.put("name", unit.getName());
        values.put("leaderUserId", unit.getLeaderUserId());
        values.put("sort", unit.getSort());
        values.put("status", unit.getStatus());
        values.put("path", unit.getPath());
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("组织变更历史序列化失败", e);
        }
    }
}
