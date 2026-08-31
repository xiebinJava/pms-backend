package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.RoleSaveCmd;
import com.brad.pms.dto.response.RoleDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {
    private final RoleService roleService;

    @GetMapping
    @RequirePermission(PermissionCode.ROLE_READ)
    public ResponseResult<List<RoleDTO>> list() { return ResponseResult.success(roleService.list()); }

    @PostMapping
    @RequirePermission(PermissionCode.ROLE_WRITE)
    public ResponseResult<RoleDTO> create(@RequestBody RoleSaveCmd cmd) { return ResponseResult.success(roleService.create(cmd)); }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.ROLE_WRITE)
    public ResponseResult<RoleDTO> update(@PathVariable Long id, @RequestBody RoleSaveCmd cmd) { return ResponseResult.success(roleService.update(id, cmd)); }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.ROLE_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) { roleService.delete(id); return ResponseResult.success(); }
}
