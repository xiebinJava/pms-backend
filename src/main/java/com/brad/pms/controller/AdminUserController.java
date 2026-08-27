package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.UserDisableCmd;
import com.brad.pms.dto.request.UserPositionCmd;
import com.brad.pms.dto.request.UserInviteCmd;
import com.brad.pms.dto.response.PersonnelDTO;
import com.brad.pms.dto.response.InvitationResponse;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.PersonnelService;
import com.brad.pms.service.InvitationService;
import com.brad.pms.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final PersonnelService personnelService;
    private final InvitationService invitationService;
    private final RoleService roleService;

    @PostMapping("/invite")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<InvitationResponse> invite(@Validated @RequestBody UserInviteCmd cmd) {
        return ResponseResult.success(invitationService.invite(cmd));
    }

    @GetMapping
    @RequirePermission(PermissionCode.USER_READ)
    public ResponseResult<List<PersonnelDTO>> list(@RequestParam(required = false) String keyword) {
        return ResponseResult.success(personnelService.list(keyword));
    }

    @PutMapping("/{id}/primary-position")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> changePrimary(@PathVariable Long id, @RequestBody UserPositionCmd cmd) {
        personnelService.changePrimaryPosition(id, cmd);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/part-time-positions")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> addPartTime(@PathVariable Long id, @RequestBody UserPositionCmd cmd) {
        personnelService.addPartTimePosition(id, cmd);
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}/part-time-positions/{positionId}")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> removePartTime(@PathVariable Long id, @PathVariable Long positionId) {
        personnelService.removePartTimePosition(id, positionId);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/roles/{roleId}")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> assignRole(@PathVariable Long id, @PathVariable Long roleId) {
        roleService.assign(id, roleId);
        return ResponseResult.success();
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> unassignRole(@PathVariable Long id, @PathVariable Long roleId) {
        roleService.unassign(id, roleId);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/disable")
    @RequirePermission(PermissionCode.USER_WRITE)
    public ResponseResult<Void> disable(@PathVariable Long id, @Validated @RequestBody UserDisableCmd cmd) {
        personnelService.disable(id, cmd);
        return ResponseResult.success();
    }
}
