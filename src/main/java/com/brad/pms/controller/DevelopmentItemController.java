package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.DevelopmentItemPageQry;
import com.brad.pms.dto.response.DevelopmentStoryListDTO;
import com.brad.pms.dto.response.DevelopmentTopicListDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.DevelopmentItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/development")
@RequiredArgsConstructor
public class DevelopmentItemController {

    private final DevelopmentItemService service;

    @PostMapping("/topics/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<DevelopmentTopicListDTO>> topics(@RequestBody(required = false) DevelopmentItemPageQry qry) {
        return ResponseResult.success(service.pageTopics(qry));
    }

    @PostMapping("/stories/page")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<PageResult<DevelopmentStoryListDTO>> stories(@RequestBody(required = false) DevelopmentItemPageQry qry) {
        return ResponseResult.success(service.pageStories(qry));
    }
}
