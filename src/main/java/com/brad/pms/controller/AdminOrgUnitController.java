package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.OrgUnitCreateCmd;
import com.brad.pms.dto.request.OrgUnitMoveCmd;
import com.brad.pms.dto.request.OrgUnitUpdateCmd;
import com.brad.pms.dto.response.OrgUnitTreeDTO;
import com.brad.pms.entity.OrgUnitHistoryDO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/org")
@RequiredArgsConstructor
public class AdminOrgUnitController {
    private final OrgUnitService orgUnitService;

    @GetMapping("/tree")
    @RequirePermission(PermissionCode.ORG_READ)
    public ResponseResult<List<OrgUnitTreeDTO>> tree() {
        return ResponseResult.success(orgUnitService.tree());
    }

    @GetMapping("/{id}/history")
    @RequirePermission(PermissionCode.ORG_READ)
    public ResponseResult<List<OrgUnitHistoryDO>> history(@PathVariable Long id) {
        return ResponseResult.success(orgUnitService.history(id));
    }

    @PostMapping
    @RequirePermission(PermissionCode.ORG_WRITE)
    public ResponseResult<OrgUnitTreeDTO> create(@Validated @RequestBody OrgUnitCreateCmd cmd) {
        return ResponseResult.success(orgUnitService.create(cmd));
    }

    @PutMapping("/{id}/move")
    @RequirePermission(PermissionCode.ORG_WRITE)
    public ResponseResult<OrgUnitTreeDTO> move(@PathVariable Long id, @RequestBody OrgUnitMoveCmd cmd) {
        return ResponseResult.success(orgUnitService.move(id, cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_WRITE)
    public ResponseResult<OrgUnitTreeDTO> update(@PathVariable Long id,
                                                 @Validated @RequestBody OrgUnitUpdateCmd cmd) {
        return ResponseResult.success(orgUnitService.update(id, cmd));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.ORG_WRITE)
    public ResponseResult<Void> deactivate(@PathVariable Long id) {
        orgUnitService.deactivate(id);
        return ResponseResult.success();
    }
}
