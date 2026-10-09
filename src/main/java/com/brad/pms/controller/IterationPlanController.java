package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.IterationPlanStatusUpdateCmd;
import com.brad.pms.dto.request.IterationPlanPageQry;
import com.brad.pms.dto.request.NodeIterationPlanCmd;
import com.brad.pms.dto.response.IterationPlanDetailDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.IterationPlanCommandService;
import com.brad.pms.service.IterationPlanService;
import com.brad.pms.service.IterationPlanOptionsService;
import org.springframework.web.bind.annotation.RequestParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class IterationPlanController {

    private final IterationPlanService iterationPlanService;
    private final IterationPlanCommandService iterationPlanCommandService;
    private final IterationPlanOptionsService iterationPlanOptionsService;

    @GetMapping("/projects/{projectId}/iteration-plans")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<NodeIterationPlanDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(iterationPlanOptionsService.forProject(projectId, java.util.Set.of()));
    }

    @GetMapping("/projects/{projectId}/iteration-system")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<IterationPlanOptionsService.SystemContext> system(@PathVariable Long projectId) {
        return ResponseResult.success(iterationPlanOptionsService.projectSystem(projectId));
    }

    @GetMapping("/iteration-plans/options")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<NodeIterationPlanDTO>> options(@RequestParam Long storyId) {
        return ResponseResult.success(iterationPlanOptionsService.forStory(storyId));
    }

    @PutMapping("/iteration-plans/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> update(@PathVariable Long id, @Validated @RequestBody NodeIterationPlanCmd cmd) {
        iterationPlanCommandService.update(id, cmd);
        return ResponseResult.success();
    }

    @PostMapping("/projects/{projectId}/iteration-plans")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Long> create(@PathVariable Long projectId,
                                        @Validated @RequestBody NodeIterationPlanCmd cmd) {
        return ResponseResult.success(iterationPlanCommandService.createForProject(projectId, cmd));
    }

    @PostMapping("/iteration-plans")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Long> createUnbound(@Validated @RequestBody NodeIterationPlanCmd cmd) {
        return ResponseResult.success(iterationPlanCommandService.create(null, null, cmd));
    }

    @PostMapping("/iteration-plans/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<IterationPlanListDTO>> page(@RequestBody(required = false) IterationPlanPageQry qry) {
        return ResponseResult.success(iterationPlanService.page(qry));
    }

    @GetMapping("/iteration-plans/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<IterationPlanDetailDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(iterationPlanService.detail(id));
    }

    @DeleteMapping("/iteration-plans/{id}")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        iterationPlanCommandService.delete(id);
        return ResponseResult.success();
    }

    @PutMapping("/iteration-plans/{id}/status")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<Void> updateStatus(@PathVariable Long id,
                                              @Validated @RequestBody IterationPlanStatusUpdateCmd cmd) {
        iterationPlanCommandService.updateStatus(id, cmd);
        return ResponseResult.success();
    }
}
