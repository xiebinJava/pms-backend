package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.SystemPageQry;
import com.brad.pms.dto.request.SystemSaveCmd;
import com.brad.pms.dto.request.SystemStatusUpdateCmd;
import com.brad.pms.dto.request.SystemVersionPageQry;
import com.brad.pms.dto.request.SystemVersionSaveCmd;
import com.brad.pms.dto.request.SystemVersionStatusUpdateCmd;
import com.brad.pms.dto.response.SystemDTO;
import com.brad.pms.dto.response.SystemListDTO;
import com.brad.pms.dto.response.SystemVersionDetailDTO;
import com.brad.pms.dto.response.SystemVersionHistoryDTO;
import com.brad.pms.dto.response.SystemVersionListDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.SystemVersionManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/development/system-versions")
@RequiredArgsConstructor
public class SystemVersionManagementController {
    private final SystemVersionManagementService service;

    @PostMapping("/systems/page")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_READ)
    public ResponseResult<PageResult<SystemListDTO>> pageSystems(@RequestBody SystemPageQry qry) {
        return ResponseResult.success(service.pageSystems(qry));
    }

    @PostMapping("/systems")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_WRITE)
    public ResponseResult<Long> createSystem(@Valid @RequestBody SystemSaveCmd cmd) {
        return ResponseResult.success(service.createSystem(cmd));
    }

    @PutMapping("/systems/{id}")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_WRITE)
    public ResponseResult<SystemDTO> updateSystem(@PathVariable Long id,
                                                   @Valid @RequestBody SystemSaveCmd cmd) {
        return ResponseResult.success(service.updateSystem(id, cmd));
    }

    @PostMapping("/systems/{id}/status")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_MANAGE)
    public ResponseResult<SystemDTO> changeSystemStatus(@PathVariable Long id,
                                                         @Valid @RequestBody SystemStatusUpdateCmd cmd) {
        return ResponseResult.success(service.changeSystemStatus(id, cmd));
    }

    @PostMapping("/page")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_READ)
    public ResponseResult<PageResult<SystemVersionListDTO>> pageVersions(@RequestBody SystemVersionPageQry qry) {
        return ResponseResult.success(service.pageVersions(qry));
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_READ)
    public ResponseResult<SystemVersionDetailDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(service.detailVersion(id));
    }

    @PostMapping
    @RequirePermission(PermissionCode.SYSTEM_VERSION_WRITE)
    public ResponseResult<Long> createVersion(@Valid @RequestBody SystemVersionSaveCmd cmd) {
        return ResponseResult.success(service.createVersion(cmd));
    }

    @PutMapping("/{id}")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_WRITE)
    public ResponseResult<SystemVersionDetailDTO> updateVersion(@PathVariable Long id,
                                                                 @Valid @RequestBody SystemVersionSaveCmd cmd) {
        return ResponseResult.success(service.updateVersion(id, cmd));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_WRITE)
    public ResponseResult<Void> deleteVersion(@PathVariable Long id) {
        service.deleteVersion(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/status")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_MANAGE)
    public ResponseResult<SystemVersionDetailDTO> changeVersionStatus(
            @PathVariable Long id, @Valid @RequestBody SystemVersionStatusUpdateCmd cmd) {
        return ResponseResult.success(service.changeVersionStatus(id, cmd));
    }

    @GetMapping("/{id}/history")
    @RequirePermission(PermissionCode.SYSTEM_VERSION_READ)
    public ResponseResult<List<SystemVersionHistoryDTO>> history(@PathVariable Long id) {
        return ResponseResult.success(service.history(id));
    }
}
