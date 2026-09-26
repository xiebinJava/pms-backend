package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.common.page.PageResult;
import com.brad.pms.dto.request.IterationPlanPageQry;
import com.brad.pms.dto.response.IterationPlanDetailDTO;
import com.brad.pms.dto.response.IterationPlanListDTO;
import com.brad.pms.dto.response.NodeIterationPlanDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.IterationPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class IterationPlanController {

    private final IterationPlanService iterationPlanService;

    @GetMapping("/projects/{projectId}/iteration-plans")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<NodeIterationPlanDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(iterationPlanService.listByProject(projectId));
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
}
