package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.DevelopmentItemNodeUpdateCmd;
import com.brad.pms.dto.request.DevelopmentItemTaskSaveCmd;
import com.brad.pms.dto.request.RequirementExecutionTargetCmd;
import com.brad.pms.dto.request.RequirementExecutionTargetOptionQry;
import com.brad.pms.dto.request.RequirementPageQry;
import com.brad.pms.dto.request.RequirementSaveCmd;
import com.brad.pms.dto.response.RequirementExecutionTargetDTO;
import com.brad.pms.dto.response.RequirementExecutionTargetHistoryDTO;
import com.brad.pms.dto.response.RequirementExecutionTargetOptionDTO;
import com.brad.pms.dto.response.RequirementListDTO;
import com.brad.pms.dto.response.DevelopmentItemWorkflowDetailDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.DevelopmentItemWorkflowService;
import com.brad.pms.service.RequirementExecutionTargetService;
import com.brad.pms.service.RequirementManagementService;
import com.brad.pms.workflow.DevelopmentItemType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/development/requirements")
@RequiredArgsConstructor
public class DevelopmentRequirementController {
    private final RequirementManagementService requirementService;
    private final RequirementExecutionTargetService targetService;
    private final DevelopmentItemWorkflowService workflowService;

    @GetMapping("/page")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<PageResult<RequirementListDTO>> page(RequirementPageQry qry) {
        return ResponseResult.success(requirementService.page(qry));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<RequirementListDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(requirementService.detail(id));
    }

    @PostMapping
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<Long> create(@Valid @RequestBody RequirementSaveCmd cmd) {
        return ResponseResult.success(requirementService.create(cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<Void> update(@PathVariable Long id, @Valid @RequestBody RequirementSaveCmd cmd) {
        requirementService.update(id, cmd);
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.REQUIREMENT_MANAGE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        requirementService.delete(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/restore")
    @RequirePermission(PermissionCode.REQUIREMENT_MANAGE)
    public ResponseResult<Void> restore(@PathVariable Long id) {
        requirementService.restore(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/execution-target/options")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<PageResult<RequirementExecutionTargetOptionDTO>> targetOptions(
            @PathVariable Long id, @RequestBody(required = false) RequirementExecutionTargetOptionQry qry) {
        return ResponseResult.success(targetService.options(id, qry));
    }

    @PostMapping("/{id}/execution-target")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<RequirementExecutionTargetDTO> link(
            @PathVariable Long id, @Valid @RequestBody RequirementExecutionTargetCmd cmd) {
        return ResponseResult.success(targetService.link(id, cmd));
    }

    @GetMapping("/{id}/execution-target")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<RequirementExecutionTargetDTO> currentTarget(@PathVariable Long id) {
        return ResponseResult.success(requirementService.detail(id).getExecutionTarget());
    }

    @DeleteMapping("/{id}/execution-target")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<RequirementExecutionTargetDTO> unlink(
            @PathVariable Long id, @RequestBody RequirementExecutionTargetCmd cmd) {
        return ResponseResult.success(targetService.unlink(id,
                cmd == null ? null : cmd.getRequirementVersion(), cmd == null ? null : cmd.getReason()));
    }

    @PostMapping("/{id}/execution-target/change")
    @RequirePermission(PermissionCode.REQUIREMENT_MANAGE)
    public ResponseResult<RequirementExecutionTargetDTO> changeTarget(
            @PathVariable Long id, @Valid @RequestBody RequirementExecutionTargetCmd cmd) {
        return ResponseResult.success(targetService.change(id, cmd));
    }

    @GetMapping("/{id}/execution-target/history")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<List<RequirementExecutionTargetHistoryDTO>> targetHistory(@PathVariable Long id) {
        return ResponseResult.success(targetService.history(id));
    }

    @GetMapping("/{id}/workflow")
    @RequirePermission(PermissionCode.REQUIREMENT_READ)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> workflow(@PathVariable Long id) {
        return ResponseResult.success(workflowService.detail(DevelopmentItemType.REQUIREMENT, id));
    }

    @PutMapping("/{id}/nodes/{nodeId}")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> updateNode(
            @PathVariable Long id, @PathVariable Long nodeId, @RequestBody DevelopmentItemNodeUpdateCmd cmd) {
        return ResponseResult.success(workflowService.updateNode(DevelopmentItemType.REQUIREMENT, id, nodeId, cmd));
    }

    @PostMapping("/{id}/nodes/{nodeId}/complete")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> completeNode(
            @PathVariable Long id, @PathVariable Long nodeId) {
        return ResponseResult.success(workflowService.completeNode(DevelopmentItemType.REQUIREMENT, id, nodeId));
    }

    @PostMapping("/{id}/nodes/{nodeId}/tasks")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> createTask(
            @PathVariable Long id, @PathVariable Long nodeId, @RequestBody DevelopmentItemTaskSaveCmd cmd) {
        return ResponseResult.success(workflowService.saveTask(DevelopmentItemType.REQUIREMENT, id, nodeId, null, cmd));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> updateTask(
            @PathVariable Long id, @PathVariable Long taskId, @RequestBody DevelopmentItemTaskSaveCmd cmd) {
        return ResponseResult.success(workflowService.saveTask(DevelopmentItemType.REQUIREMENT, id, null, taskId, cmd));
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    @RequirePermission(PermissionCode.REQUIREMENT_WRITE)
    public ResponseResult<DevelopmentItemWorkflowDetailDTO> deleteTask(
            @PathVariable Long id, @PathVariable Long taskId) {
        return ResponseResult.success(workflowService.deleteTask(DevelopmentItemType.REQUIREMENT, id, taskId));
    }
}
