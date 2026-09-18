package com.brad.pms.controller;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandDescriptor;
import com.brad.pms.ai.command.PmsCommandMetadata;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshCommandCapabilityDTO;
import com.brad.pms.integration.dsh.api.DshCapabilityDTO;
import com.brad.pms.integration.dsh.api.DshQueryCapabilityDTO;
import com.brad.pms.integration.dsh.security.DshAgentScopePolicy;
import com.brad.pms.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Predicate;

@RestController
@RequestMapping("/integration/dsh/v1")
public class DshCapabilityController {

    @GetMapping("/capabilities")
    public ResponseResult<DshCapabilityDTO> capabilities() {
        Predicate<String> scopeAvailable = this::scopeAvailable;
        List<DshCommandCapabilityDTO> commands = Arrays.stream(CommandName.values())
                .map(PmsCommandMetadata::descriptor)
                .filter(descriptor -> descriptor.scopes().stream().allMatch(scopeAvailable))
                .map(DshCapabilityController::toCapability)
                .toList();
        List<DshQueryCapabilityDTO> queries = List.of(
                new DshQueryCapabilityDTO(
                        "projects", "查询项目列表", List.of(
                        "projectId", "projectNo", "projectName", "status", "statusLabel",
                        "priority", "priorityLabel", "currentNodeKey", "currentNodeName",
                        "projectManagerName", "startDate", "endDate", "orgUnitName"), List.of(
                        "keyword", "status", "priority", "projectManagerId", "orgUnitId", "currentNodeKey"),
                        List.of("pms:query:read"), 100),
                new DshQueryCapabilityDTO(
                        "tasks", "查询项目任务", List.of(
                        "taskId", "title", "status", "statusLabel", "projectId", "nodeId",
                        "assigneeName", "dueDate", "priority"), List.of(
                        "projectId", "nodeId", "due", "status"), List.of("pms:query:read"), 100));

        boolean queryAvailable = scopeAvailable("pms:query:read");
        List<String> tools = new ArrayList<>(List.of(
                "pms_project_list", "pms_project_get", "pms_task_list"));
        if (queryAvailable) tools.add("pms_query");
        if (commands.stream().anyMatch(DshCommandCapabilityDTO::supportsPreview)) {
            tools.add("pms_command_preview");
        }
        if (commands.stream().anyMatch(DshCommandCapabilityDTO::supportsExecute)) {
            tools.add("pms_command_execute");
        }

        LinkedHashSet<String> scopes = new LinkedHashSet<>();
        DshAgentScopePolicy.PROJECT_ASSISTANT_SCOPES.stream()
                .filter(scopeAvailable)
                .forEach(scopes::add);
        return ResponseResult.success(new DshCapabilityDTO(
                "v1",
                tools,
                List.copyOf(scopes),
                List.of(
                        "project-list",
                        "project-detail",
                        "project-dashboard",
                        "workflow-template"),
                queryAvailable ? queries : List.of(),
                commands));
    }

    private boolean scopeAvailable(String scope) {
        // The endpoint is protected by AiDelegationRoutePolicy. For direct
        // controller calls (including startup/tests), publish the full Agent
        // contract; for a DSH token, publish only its delegated scopes.
        return !UserContext.isDshDelegation() || UserContext.hasDshScope(scope);
    }

    private static DshCommandCapabilityDTO toCapability(PmsCommandDescriptor descriptor) {
        return new DshCommandCapabilityDTO(
                descriptor.name().code(),
                descriptor.description(),
                descriptor.access(),
                descriptor.risk(),
                descriptor.requiresConfirmation(),
                descriptor.scopes(),
                descriptor.parameters(),
                descriptor.supportsPreview(),
                descriptor.supportsExecute(),
                descriptor.refreshScopes());
    }
}
