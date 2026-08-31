package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.response.OrgUnitTreeDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 项目页面使用的只读组织树；避免普通项目成员依赖后台管理权限。 */
@RestController
@RequestMapping("/org")
@RequiredArgsConstructor
public class OrgTreeController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/tree")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<OrgUnitTreeDTO>> tree() {
        return ResponseResult.success(orgUnitService.tree());
    }
}
